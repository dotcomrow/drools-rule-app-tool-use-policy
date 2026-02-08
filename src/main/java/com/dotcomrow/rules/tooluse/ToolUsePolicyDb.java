package com.dotcomrow.rules.tooluse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
                                + "&sslmode=disable";
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
                throw new IllegalStateException(
                        "Missing DB credentials. Set TOOL_USE_POLICY_DB_USERNAME + TOOL_USE_POLICY_DB_PASSWORD, "
                                + "or set TOOL_USE_POLICY_DB_CREDS_JSON/TOOL_USE_POLICY_DB_CREDS_FILE to a Vault static-creds JSON file.");
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
}

