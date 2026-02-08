package com.dotcomrow.rules.tooluse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small JDBC helper that demonstrates how a rules app can use a per-app YugabyteDB database.
 *
 * Runtime expectations:
 * - Provide connection info via env vars (preferred), or point to a JSON file that contains Vault
 *   static creds output (".data.username" + ".data.password") and keep it refreshed externally.
 */
public final class ToolUsePolicyDb {
    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern JSON_STRING_FIELD =
            Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"", Pattern.MULTILINE);

    private ToolUsePolicyDb() {}

    public static DbTestResult runDbTest(String requestId) {
        final DbConfig cfg;
        try {
            cfg = DbConfig.fromEnv();
        } catch (Exception e) {
            return DbTestResult.failure("DB config error: " + e.getMessage());
        }

        // The PostgreSQL JDBC driver is expected to be available via the KJAR's Maven dependencies.
        // Attempt to load it explicitly so DriverManager can find it even if the container hasn't initialized
        // DB connectivity yet.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {
            // Keep going. If no driver is available at runtime, we'll surface a useful error from the connection call.
        }

        try (Connection conn = DriverManager.getConnection(cfg.jdbcUrl, cfg.username, cfg.password)) {
            conn.setAutoCommit(true);

            // Ensure schema/table exist. The deployment job should do this already, but keeping the test idempotent
            // helps when debugging.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + cfg.schema);
                st.execute(
                        "CREATE TABLE IF NOT EXISTS "
                                + cfg.schema
                                + ".tool_use_audit ("
                                + "id bigserial PRIMARY KEY,"
                                + "request_id text NOT NULL,"
                                + "decision text NOT NULL,"
                                + "created_at timestamptz NOT NULL DEFAULT now()"
                                + ")");
            }

            try (PreparedStatement ps =
                    conn.prepareStatement(
                            "INSERT INTO " + cfg.schema + ".tool_use_audit (request_id, decision) VALUES (?, ?)")) {
                ps.setString(1, requestId);
                ps.setString(2, "DB_TEST");
                ps.executeUpdate();
            }

            long count;
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM " + cfg.schema + ".tool_use_audit");
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                count = rs.getLong(1);
            }

            return DbTestResult.success(
                    "OK: db write/read succeeded (rows="
                            + count
                            + ", schema="
                            + cfg.schema
                            + ", db="
                            + cfg.dbName
                            + ")");
        } catch (Exception e) {
            return DbTestResult.failure("DB error: " + e.getClass().getSimpleName() + ": " + safeMsg(e));
        }
    }

    private static String safeMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "(no message)";
        }
        // Keep messages short to avoid overloading KIE server responses/logs.
        msg = msg.replace('\n', ' ').replace('\r', ' ');
        if (msg.length() > 400) {
            return msg.substring(0, 400) + "...";
        }
        return msg;
    }

    private static final class DbConfig {
        private static final Object VAULT_LOCK = new Object();
        private static volatile CachedCreds cachedVaultCreds;

        final String jdbcUrl;
        final String dbName;
        final String schema;
        final String username;
        final String password;

        private DbConfig(String jdbcUrl, String dbName, String schema, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.dbName = dbName;
            this.schema = schema;
            this.username = username;
            this.password = password;
        }

        static DbConfig fromEnv() throws IOException {
            String url = env("TOOL_USE_POLICY_DB_URL");
            String host = env("TOOL_USE_POLICY_DB_HOST", "yb-tserver-service.yugabyte.svc.cluster.local");
            String port = env("TOOL_USE_POLICY_DB_PORT", "5433");
            String dbName = env("TOOL_USE_POLICY_DB_NAME", "rules_tool_use");
            String schema = env("TOOL_USE_POLICY_DB_SCHEMA", "tool_use");
            String sslmode = env("TOOL_USE_POLICY_DB_SSLMODE", "disable");

            validateIdent("schema", schema);
            validateIdent("dbName", dbName);

            if (url == null || url.isBlank()) {
                url =
                        "jdbc:postgresql://"
                                + host
                                + ":"
                                + port
                                + "/"
                                + dbName
                                + "?currentSchema="
                                + schema
                                + "&sslmode="
                                + sslmode;
            }

            String username = env("TOOL_USE_POLICY_DB_USERNAME");
            String password = env("TOOL_USE_POLICY_DB_PASSWORD");
            if (isBlank(username) || isBlank(password)) {
                String jsonPath = env("TOOL_USE_POLICY_DB_CREDS_JSON");
                if (isBlank(jsonPath)) {
                    jsonPath = env("TOOL_USE_POLICY_DB_CREDS_FILE");
                }
                if (!isBlank(jsonPath)) {
                    String json = Files.readString(Path.of(jsonPath), StandardCharsets.UTF_8);
                    String u = jsonStringField(json, "username");
                    String p = jsonStringField(json, "password");
                    if (!isBlank(u) && !isBlank(p)) {
                        username = u;
                        password = p;
                    }
                }
            }

            if (isBlank(username) || isBlank(password)) {
                // Final fallback: fetch static creds directly from Vault using Kubernetes auth.
                // This avoids needing per-app env var wiring in the KIE server deployment.
                String vaultError = null;
                try {
                    VaultCreds creds = vaultStaticCreds();
                    if (creds != null && !isBlank(creds.username) && !isBlank(creds.password)) {
                        username = creds.username;
                        password = creds.password;
                    }
                } catch (Exception e) {
                    vaultError = e.getMessage();
                }

                if (isBlank(username) || isBlank(password)) {
                    throw new IllegalStateException(
                            "Missing DB credentials. Set TOOL_USE_POLICY_DB_USERNAME + TOOL_USE_POLICY_DB_PASSWORD, "
                                    + "or set TOOL_USE_POLICY_DB_CREDS_JSON/TOOL_USE_POLICY_DB_CREDS_FILE to a Vault static-creds JSON file."
                                    + (vaultError == null ? "" : " Vault lookup failed: " + vaultError));
                }
            }

            return new DbConfig(url, dbName, schema, username, password);
        }

        private static void validateIdent(String name, String value) {
            if (isBlank(value) || !SAFE_IDENT.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid " + name + " identifier: " + value);
            }
        }

        private static String jsonStringField(String json, String key) {
            Pattern p = Pattern.compile(String.format(Locale.ROOT, JSON_STRING_FIELD.pattern(), Pattern.quote(key)));
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }

        private static VaultCreds vaultStaticCreds() throws IOException, InterruptedException {
            String enabled = env("TOOL_USE_POLICY_VAULT_ENABLED", "true");
            if ("false".equalsIgnoreCase(enabled)) {
                return null;
            }

            int cacheSeconds = 60;
            try {
                cacheSeconds = Integer.parseInt(env("TOOL_USE_POLICY_VAULT_CACHE_SECONDS", "60"));
            } catch (NumberFormatException ignored) {
                cacheSeconds = 60;
            }

            long now = System.currentTimeMillis();
            CachedCreds c = cachedVaultCreds;
            if (c != null && now < c.expiresAtMs) {
                return c.creds;
            }

            synchronized (VAULT_LOCK) {
                c = cachedVaultCreds;
                if (c != null && now < c.expiresAtMs) {
                    return c.creds;
                }
                VaultCreds fetched = fetchVaultStaticCreds();
                if (fetched == null) {
                    return null;
                }
                cachedVaultCreds = new CachedCreds(fetched, now + (cacheSeconds * 1000L));
                return fetched;
            }
        }

        private static VaultCreds fetchVaultStaticCreds() throws IOException, InterruptedException {
            String addr = envAny(new String[] {"TOOL_USE_POLICY_VAULT_ADDR", "VAULT_ADDR"}, "http://vault.vault.svc.cluster.local:8200");
            String authPath = env("TOOL_USE_POLICY_VAULT_AUTH_PATH", "kubernetes");
            String role = env("TOOL_USE_POLICY_VAULT_ROLE", "drools");
            String jwtPath = env("TOOL_USE_POLICY_VAULT_JWT_PATH", "/var/run/secrets/kubernetes.io/serviceaccount/token");

            String dbMount = env("TOOL_USE_POLICY_VAULT_DB_MOUNT", "yugabyte-db");
            String staticRole = env("TOOL_USE_POLICY_VAULT_STATIC_ROLE", "tool-use-policy-yb-app");

            int timeoutSeconds = 5;
            try {
                timeoutSeconds = Integer.parseInt(env("TOOL_USE_POLICY_VAULT_TIMEOUT_SECONDS", "5"));
            } catch (NumberFormatException ignored) {
                timeoutSeconds = 5;
            }

            String jwt = Files.readString(Path.of(jwtPath), StandardCharsets.UTF_8).trim();
            if (jwt.isBlank()) {
                throw new IllegalStateException("Vault JWT is empty at " + jwtPath);
            }

            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                            .build();

            String loginUrl = addr + "/v1/auth/" + authPath + "/login";
            String loginPayload = "{\"role\":\"" + role + "\",\"jwt\":\"" + jwt + "\"}";
            HttpRequest loginReq =
                    HttpRequest.newBuilder(URI.create(loginUrl))
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                            .build();
            HttpResponse<String> loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (loginResp.statusCode() != 200) {
                throw new IllegalStateException("Vault login failed: HTTP " + loginResp.statusCode());
            }

            String token = jsonStringField(loginResp.body(), "client_token");
            if (isBlank(token)) {
                throw new IllegalStateException("Vault login response missing client_token");
            }

            String credsUrl = addr + "/v1/" + dbMount + "/static-creds/" + staticRole;
            HttpRequest credsReq =
                    HttpRequest.newBuilder(URI.create(credsUrl))
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .header("X-Vault-Token", token)
                            .GET()
                            .build();
            HttpResponse<String> credsResp = client.send(credsReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (credsResp.statusCode() != 200) {
                throw new IllegalStateException("Vault static-creds read failed: HTTP " + credsResp.statusCode());
            }

            String u = jsonStringField(credsResp.body(), "username");
            String p = jsonStringField(credsResp.body(), "password");
            if (isBlank(u) || isBlank(p)) {
                throw new IllegalStateException("Vault static-creds response missing username/password");
            }
            return new VaultCreds(u, p);
        }

        private static String envAny(String[] keys, String defaultValue) {
            for (String k : keys) {
                String v = System.getenv(k);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return defaultValue;
        }

        private static String env(String key) {
            return System.getenv(key);
        }

        private static String env(String key, String defaultValue) {
            String v = env(key);
            if (v == null || v.isBlank()) {
                return defaultValue;
            }
            return v;
        }

        private static boolean isBlank(String v) {
            return v == null || v.isBlank();
        }
    }

    private static final class VaultCreds {
        final String username;
        final String password;

        VaultCreds(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static final class CachedCreds {
        final VaultCreds creds;
        final long expiresAtMs;

        CachedCreds(VaultCreds creds, long expiresAtMs) {
            this.creds = creds;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
