package com.dotcomrow.rules.tooluse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal example of a rules app "touching the DB" using:
 * - Vault Kubernetes auth (service account JWT)
 * - Vault database secrets engine static role credentials (rotating password, static username)
 * - JDBC to YugabyteDB (YSQL / Postgres wire protocol)
 *
 * This intentionally avoids extra runtime dependencies (no JSON library, no ORM) so the KJAR stays simple.
 */
public final class ToolUsePolicyDb {
  private static final Path K8S_SA_TOKEN = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private ToolUsePolicyDb() {
  }

  public static DbTestResult runDbTest(String message) {
    final String requestId = UUID.randomUUID().toString();
    final String dbName = envFirst("TOOL_USE_POLICY_DB_NAME", null, "rules_tool_use");
    final String schema = envFirst("TOOL_USE_POLICY_DB_SCHEMA", null, "tool_use");
    String dbUser = null;

    try {
      final String vaultAddr = envFirst("TOOL_USE_POLICY_VAULT_ADDR", "VAULT_ADDR", "http://vault.vault.svc.cluster.local:8200");
      final String vaultK8sRole = envFirst("TOOL_USE_POLICY_VAULT_ROLE", null, "drools");
      final String vaultStaticRole = envFirst("TOOL_USE_POLICY_VAULT_STATIC_ROLE", null, "tool-use-policy-yb-app");

      final String host = envFirst("TOOL_USE_POLICY_DB_HOST", null, "yb-tserver-service.yugabyte.svc.cluster.local");
      final String port = envFirst("TOOL_USE_POLICY_DB_PORT", null, "5433");
      final String sslmode = envFirst("TOOL_USE_POLICY_DB_SSLMODE", null, "disable");

      final String jwt = readFileTrimmed(K8S_SA_TOKEN);
      final String vaultToken = vaultLoginKubernetes(vaultAddr, vaultK8sRole, jwt);
      final DbCreds creds = vaultReadStaticCreds(vaultAddr, vaultToken, vaultStaticRole);
      dbUser = creds.username;

      // If the driver exists on the KIE Server classpath, this helps in environments where it doesn't auto-register.
      try {
        Class.forName("org.postgresql.Driver");
      } catch (ClassNotFoundException ignored) {
      }

      final String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName
          + "?sslmode=" + urlEncodeQueryValue(sslmode)
          + "&currentSchema=" + urlEncodeQueryValue(schema);

      try (Connection conn = DriverManager.getConnection(url, creds.username, creds.password)) {
        conn.setAutoCommit(false);

        final String qSchema = quoteIdent(schema);
        try (Statement st = conn.createStatement()) {
          st.execute("CREATE SCHEMA IF NOT EXISTS " + qSchema + ";");
          st.execute("CREATE TABLE IF NOT EXISTS " + qSchema + ".tool_use_audit ("
              + "id bigserial PRIMARY KEY,"
              + "request_id text NOT NULL,"
              + "decision text NOT NULL,"
              + "created_at timestamptz NOT NULL DEFAULT now()"
              + ");");
        }

        final String decision = (message == null || message.trim().isEmpty()) ? "DB_TEST" : message.trim();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO " + qSchema + ".tool_use_audit (request_id, decision) VALUES (?, ?)")) {
          ps.setString(1, requestId);
          ps.setString(2, decision);
          ps.executeUpdate();
        }

        conn.commit();
      }

      return new DbTestResult(true, dbUser, dbName, schema, requestId, null);
    } catch (Exception e) {
      return new DbTestResult(false, dbUser, dbName, schema, requestId, compactError(e));
    }
  }

  private static String envFirst(String keyA, String keyB, String defaultValue) {
    String v = System.getenv(keyA);
    if (v != null && !v.trim().isEmpty()) {
      return v.trim();
    }
    if (keyB != null) {
      v = System.getenv(keyB);
      if (v != null && !v.trim().isEmpty()) {
        return v.trim();
      }
    }
    return defaultValue;
  }

  private static String readFileTrimmed(Path p) throws IOException {
    return Files.readString(p, StandardCharsets.UTF_8).trim();
  }

  private static String vaultLoginKubernetes(String vaultAddr, String role, String jwt) throws IOException, InterruptedException {
    final String url = joinUrl(vaultAddr, "/v1/auth/kubernetes/login");
    final String body = "{\"role\":\"" + jsonEscape(role) + "\",\"jwt\":\"" + jsonEscape(jwt) + "\"}";

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("Vault kubernetes login failed: HTTP " + resp.statusCode());
    }

    String token = extractJsonString(resp.body(), "client_token");
    if (token == null || token.isEmpty()) {
      throw new IOException("Vault kubernetes login response missing client_token");
    }
    return token;
  }

  private static DbCreds vaultReadStaticCreds(String vaultAddr, String vaultToken, String staticRoleName)
      throws IOException, InterruptedException {
    final String url = joinUrl(vaultAddr, "/v1/yugabyte-db/static-creds/" + staticRoleName);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("X-Vault-Token", vaultToken)
        .GET()
        .build();

    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("Vault static-creds read failed: HTTP " + resp.statusCode());
    }

    String user = extractJsonString(resp.body(), "username");
    String pass = extractJsonString(resp.body(), "password");
    if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
      throw new IOException("Vault static-creds response missing username/password");
    }
    return new DbCreds(user, pass);
  }

  private static String joinUrl(String base, String path) {
    String b = Objects.requireNonNull(base, "base").trim();
    String p = Objects.requireNonNull(path, "path").trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    return b + p;
  }

  private static String quoteIdent(String ident) {
    if (ident == null) {
      throw new IllegalArgumentException("identifier is null");
    }
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  private static String urlEncodeQueryValue(String s) {
    // Minimal percent-encoding for query values used here (sslmode/currentSchema).
    // Keep it small to avoid pulling in extra deps.
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '-' || c == '_' || c == '.' || c == '~') {
        out.append(c);
      } else {
        out.append('%');
        out.append(String.format("%02X", (int) c));
      }
    }
    return out.toString();
  }

  private static String jsonEscape(String s) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\':
          out.append("\\\\");
          break;
        case '"':
          out.append("\\\"");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          out.append(c);
      }
    }
    return out.toString();
  }

  private static String extractJsonString(String json, String key) {
    String needle = "\"" + key + "\"";
    int i = json.indexOf(needle);
    if (i < 0) {
      return null;
    }
    i = json.indexOf(':', i + needle.length());
    if (i < 0) {
      return null;
    }
    i++;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
      i++;
    }
    if (i >= json.length() || json.charAt(i) != '"') {
      return null;
    }
    i++;

    StringBuilder out = new StringBuilder();
    boolean esc = false;
    while (i < json.length()) {
      char c = json.charAt(i++);
      if (esc) {
        switch (c) {
          case '"':
            out.append('"');
            break;
          case '\\':
            out.append('\\');
            break;
          case '/':
            out.append('/');
            break;
          case 'b':
            out.append('\b');
            break;
          case 'f':
            out.append('\f');
            break;
          case 'n':
            out.append('\n');
            break;
          case 'r':
            out.append('\r');
            break;
          case 't':
            out.append('\t');
            break;
          case 'u':
            if (i + 4 > json.length()) {
              return null;
            }
            String hex = json.substring(i, i + 4);
            try {
              out.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
              return null;
            }
            i += 4;
            break;
          default:
            out.append(c);
        }
        esc = false;
        continue;
      }
      if (c == '\\') {
        esc = true;
        continue;
      }
      if (c == '"') {
        return out.toString();
      }
      out.append(c);
    }
    return null;
  }

  private static String compactError(Exception e) {
    String msg = e.getMessage();
    if (msg == null || msg.trim().isEmpty()) {
      msg = e.getClass().getName();
    }
    // Don't dump full stack traces into rule output; keep it readable for the Workbench / KIE Server UI.
    return msg.length() > 500 ? msg.substring(0, 500) : msg;
  }

  private static final class DbCreds {
    final String username;
    final String password;

    DbCreds(String username, String password) {
      this.username = Objects.requireNonNull(username, "username");
      this.password = Objects.requireNonNull(password, "password");
    }
  }
}

