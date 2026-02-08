package com.dotcomrow.rules.tooluse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Pattern;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Small JDBC helper that demonstrates how a rules app can use a per-app YugabyteDB database.
 *
 * Runtime expectations:
 * - The KIE Server/WildFly runtime provides a server-managed datasource that points at the app DB.
 * - The rules app looks it up via JNDI and uses pooled connections.
 */
public final class ToolUsePolicyDb {
    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final String DEFAULT_JNDI = "java:jboss/datasources/ToolUsePolicyDS";
    private static final String DEFAULT_SCHEMA = "tool_use";

    private ToolUsePolicyDb() {}

    public static DbTestResult runDbTest(String requestId) {
        try {
            DataSource ds = lookupDataSource();
            String schema = env("TOOL_USE_POLICY_DB_SCHEMA", DEFAULT_SCHEMA);
            validateIdent("schema", schema);
            String jndi = env("TOOL_USE_POLICY_DB_JNDI", DEFAULT_JNDI);

            try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(true);

            // Ensure schema/table exist. The deployment job should do this already, but keeping the test idempotent
            // helps when debugging.
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                st.execute(
                        "CREATE TABLE IF NOT EXISTS "
                                + schema
                                + ".tool_use_audit ("
                                + "id bigserial PRIMARY KEY,"
                                + "request_id text NOT NULL,"
                                + "decision text NOT NULL,"
                                + "created_at timestamptz NOT NULL DEFAULT now()"
                                + ")");
            }

            try (PreparedStatement ps =
                    conn.prepareStatement(
                            "INSERT INTO " + schema + ".tool_use_audit (request_id, decision) VALUES (?, ?)")) {
                ps.setString(1, requestId);
                ps.setString(2, "DB_TEST");
                ps.executeUpdate();
            }

            long count;
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM " + schema + ".tool_use_audit");
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                count = rs.getLong(1);
            }

            return DbTestResult.success(
                    "OK: db write/read succeeded (rows="
                            + count
                            + ", schema=" + schema
                            + ", jndi=" + jndi
                            + ")");
            }
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

    private static DataSource lookupDataSource() throws NamingException {
        String jndi = env("TOOL_USE_POLICY_DB_JNDI", DEFAULT_JNDI);
        Object obj = new InitialContext().lookup(jndi);
        if (!(obj instanceof DataSource)) {
            throw new NamingException("JNDI lookup " + jndi + " did not return a DataSource");
        }
        return (DataSource) obj;
    }

    private static void validateIdent(String name, String value) {
        if (value == null || value.isBlank() || !SAFE_IDENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name + " identifier: " + value);
        }
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }
}
