package com.dbvault.app.db;

import android.util.Log;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 远程库会话：直接走 JDBC（TCP 直连，不经过任何中转服务）。
 * Remote session over plain JDBC — connects straight to the server, no relay.
 */
public class JdbcSession implements DatabaseSession {

    private static final String TAG = "JdbcSession";
    private static final int CONNECT_TIMEOUT_MS = 8000;

    private final Connection conn;
    private final String dialect;
    private final String label;
    private final String catalog;   // MySQL: 库名；PG: null
    private final String schema;    // PG: schema；MySQL: null
    private final boolean readOnly;
    private volatile boolean closed;

    public JdbcSession(ConnectionProfile p) throws Exception {
        this.dialect = p.type;
        this.label = p.displayName();
        boolean usesCatalog = ConnectionProfile.TYPE_MYSQL.equals(p.type)
                || ConnectionProfile.TYPE_MSSQL.equals(p.type);
        this.catalog = usesCatalog
                ? (isEmpty(p.database) ? null : p.database)
                : null;
        if (ConnectionProfile.TYPE_POSTGRES.equals(p.type)) {
            this.schema = isEmpty(p.schema) ? "public" : p.schema;
        } else if (ConnectionProfile.TYPE_MSSQL.equals(p.type)) {
            this.schema = isEmpty(p.schema) ? "dbo" : p.schema;
        } else {
            this.schema = null;
        }

        String url = buildUrl(p);
        Properties props = new Properties();
        props.setProperty("user", p.user == null ? "" : p.user);
        props.setProperty("password", p.password == null ? "" : p.password);

        String driverClass = ConnectionProfile.TYPE_POSTGRES.equals(p.type)
                ? "org.postgresql.Driver"
                : ConnectionProfile.TYPE_MSSQL.equals(p.type)
                ? "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                : "org.mariadb.jdbc.Driver";

        long t0 = System.currentTimeMillis();
        Driver driver = (Driver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
        Connection c = driver.connect(url, props);
        if (c == null) {
            throw new IllegalStateException("驱动拒绝了连接 / driver rejected the url: " + url);
        }
        Log.i(TAG, "connected in " + (System.currentTimeMillis() - t0) + "ms class=" + c.getClass().getName());
        this.conn = c;
        try {
            c.setAutoCommit(true);
        } catch (Exception ignored) {
        }
        boolean ro = false;
        try {
            ro = c.isReadOnly();
        } catch (Exception ignored) {
        }
        this.readOnly = ro;
    }

    private static String buildUrl(ConnectionProfile p) {
        String host = p.host == null ? "" : p.host.trim();
        String db = p.database == null ? "" : p.database.trim();
        int port = p.port > 0 ? p.port : ConnectionProfile.defaultPort(p.type);

        if (ConnectionProfile.TYPE_MSSQL.equals(p.type)) {
            StringBuilder sb = new StringBuilder("jdbc:sqlserver://").append(host).append(':').append(port);
            if (!db.isEmpty()) sb.append(";databaseName=").append(db);
            sb.append(";encrypt=").append(p.ssl)
              .append(";trustServerCertificate=true")
              .append(";loginTimeout=").append(CONNECT_TIMEOUT_MS / 1000)
              .append(";socketTimeout=20000")
              .append(";applicationName=DBVault");
            return sb.toString();
        }

        if (ConnectionProfile.TYPE_POSTGRES.equals(p.type)) {
            StringBuilder sb = new StringBuilder("jdbc:postgresql://").append(host).append(':').append(port);
            if (!db.isEmpty()) sb.append('/').append(db);
            sb.append("?connectTimeout=").append(CONNECT_TIMEOUT_MS / 1000)
              .append("&socketTimeout=20")
              .append("&sslmode=").append(p.ssl ? "require" : "disable")
              .append("&ApplicationName=DBVault");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder("jdbc:mariadb://").append(host).append(':').append(port);
        if (!db.isEmpty()) sb.append('/').append(db);
        sb.append("?connectTimeout=").append(CONNECT_TIMEOUT_MS)
          .append("&socketTimeout=20000")
          .append("&useSsl=").append(p.ssl)
          .append("&allowPublicKeyRetrieval=true")
          .append("&tcpKeepAlive=true")
          .append("&useUnicode=true")
          .append("&characterEncoding=utf8");
        return sb.toString();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public String title() {
        return label;
    }

    @Override
    public String dialect() {
        return dialect;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public List<DbObject> objects() throws Exception {
        List<DbObject> out = new ArrayList<>();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                String type = rs.getString("TABLE_TYPE");
                boolean view = type != null && type.toUpperCase(Locale.ROOT).contains("VIEW");
                out.add(new DbObject(name, view ? DbObject.TYPE_VIEW : DbObject.TYPE_TABLE, -1));
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    @Override
    public List<ColumnMeta> columns(String table) throws Exception {
        Map<String, Integer> pk = primaryKeys(table);
        List<ColumnMeta> out = new ArrayList<>();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                String nullable = rs.getString("IS_NULLABLE");
                int pos = rs.getInt("ORDINAL_POSITION");
                Integer pkOrder = pk.get(name);
                out.add(new ColumnMeta(name, type,
                        nullable == null || !"NO".equalsIgnoreCase(nullable),
                        pkOrder != null, pos));
            }
        }
        return out;
    }

    private Map<String, Integer> primaryKeys(String table) {
        Map<String, Integer> map = new HashMap<>();
        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                int seq = rs.getInt("KEY_SEQ");
                map.put(col, seq);
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    @Override
    public QueryResult query(String sql, int maxRows) throws Exception {
        long t0 = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            int budget = Math.max(1, maxRows);
            try {
                st.setMaxRows(budget + 1);
            } catch (Exception ignored) {
            }
            if (!isReadStatement(sql)) {
                int affected = st.executeUpdate(sql);
                return new QueryResult(new String[]{"status"},
                        new String[][]{{"OK · 影响 " + affected + " 行"}}, false,
                        System.currentTimeMillis() - t0);
            }
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                String[] cols = new String[n];
                for (int i = 1; i <= n; i++) cols[i - 1] = md.getColumnLabel(i);

                List<String[]> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (rows.size() >= budget) {
                        truncated = true;
                        break;
                    }
                    String[] row = new String[n];
                    for (int i = 1; i <= n; i++) row[i - 1] = stringify(rs, i);
                    rows.add(row);
                }
                return new QueryResult(cols, rows.toArray(new String[0][]), truncated,
                        System.currentTimeMillis() - t0);
            }
        }
    }

    private static String stringify(ResultSet rs, int i) {
        try {
            Object o = rs.getObject(i);
            if (o == null) return null;
            if (o instanceof byte[]) {
                byte[] b = (byte[]) o;
                StringBuilder sb = new StringBuilder("0x");
                int limit = Math.min(b.length, 24);
                for (int k = 0; k < limit; k++) sb.append(String.format("%02X", b[k]));
                if (b.length > limit) sb.append("…(").append(b.length).append("B)");
                return sb.toString();
            }
            String s = String.valueOf(o);
            return s.indexOf('\n') >= 0 ? s.replace('\n', ' ') : s;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isReadStatement(String sql) {
        String s = sql == null ? "" : sql.trim().toUpperCase(Locale.ROOT);
        return s.startsWith("SELECT") || s.startsWith("SHOW") || s.startsWith("DESC")
                || s.startsWith("EXPLAIN") || s.startsWith("WITH") || s.startsWith("VALUES");
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) throws Exception {
        return query(Sql.pageQuery(dialect, quote(table), offset, limit), limit + 1);
    }

    @Override
    public int update(String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    @Override
    public String quote(String identifier) {
        return Sql.quote(dialect, identifier);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 供 UI 展示服务器版本等
     * expose server info for the header
     */
    public String serverInfo() {
        try {
            return conn.getMetaData().getDatabaseProductName() + " "
                    + conn.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            return "";
        }
    }

    public Set<String> listSchemas() throws Exception {
        Set<String> out = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) out.add(rs.getString("TABLE_SCHEM"));
        }
        return out;
    }
}
