package com.dbvault.app.db;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 会话：自带 RESP 协议实现，不引入第三方客户端。
 * 键值空间被映射成一张虚拟表（key / type / value），SQL 控制台里可以直接敲 Redis 命令。
 * Redis session with a hand-written RESP client. Keys are exposed as a virtual table
 * (key / type / value) and the console accepts raw Redis commands.
 */
public class RedisSession implements DatabaseSession {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int SO_TIMEOUT_MS = 20000;
    private static final int MAX_SCAN = 20000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final String label;
    private final int dbIndex;
    private volatile boolean closed;

    public RedisSession(ConnectionProfile p) throws Exception {
        this.label = p.displayName();
        this.dbIndex = parseIntSafe(p.database, 0);

        socket = new Socket();
        socket.connect(new InetSocketAddress(p.host, p.port > 0 ? p.port : 6379), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(SO_TIMEOUT_MS);
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();

        if (p.password != null && !p.password.trim().isEmpty()) {
            Object r = exec("AUTH", p.password.trim());
            if (r == null) throw new IOException("AUTH rejected");
        }
        if (dbIndex > 0) {
            Object r = exec("SELECT", String.valueOf(dbIndex));
            if (r == null) throw new IOException("SELECT rejected");
        }
        exec("PING");
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- RESP

    private synchronized Object exec(String... args) throws IOException {
        send(args);
        return readReply();
    }

    private void send(String[] args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (String a : args) {
            byte[] b = a.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(b.length).append("\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.write('\r');
            out.write('\n');
            sb.setLength(0);
        }
        out.flush();
    }

    private Object readReply() throws IOException {
        int tag = in.read();
        if (tag < 0) throw new IOException("connection closed by server");
        switch (tag) {
            case '+':
                return readLine();
            case '-':
                throw new IOException(readLine());
            case ':':
                return Long.parseLong(readLine());
            case '$': {
                int len = Integer.parseInt(readLine());
                if (len < 0) return null;
                byte[] buf = new byte[len];
                readFully(buf);
                readCrLf();
                return new String(buf, StandardCharsets.UTF_8);
            }
            case '*': {
                int n = Integer.parseInt(readLine());
                if (n < 0) return null;
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(readReply());
                return list;
            }
            default:
                throw new IOException("unexpected reply tag: " + (char) tag);
        }
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) throw new IOException("EOF");
            off += r;
        }
    }

    private void readCrLf() throws IOException {
        in.read();
        in.read();
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) throw new IOException("EOF");
            if (c == '\r') {
                in.read();
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ session

    @Override
    public String title() {
        return label;
    }

    @Override
    public String dialect() {
        return "redis";
    }

    @Override
    public String subtitle() {
        return label + " · db" + dbIndex;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<DbObject> objects() throws Exception {
        long size;
        try {
            Object r = exec("DBSIZE");
            size = r instanceof Long ? (Long) r : -1;
        } catch (Exception e) {
            size = -1;
        }
        List<DbObject> out = new ArrayList<>();
        out.add(new DbObject("keys", DbObject.TYPE_TABLE, size));
        return out;
    }

    @Override
    public List<ColumnMeta> columns(String table) {
        List<ColumnMeta> out = new ArrayList<>();
        out.add(new ColumnMeta("key", "string", false, true, 0));
        out.add(new ColumnMeta("type", "string", false, false, 1));
        out.add(new ColumnMeta("value", "string", true, false, 2));
        return out;
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) throws Exception {
        long t0 = System.currentTimeMillis();
        List<String> keys = scanKeys(offset + limit);
        Collections.sort(keys);

        List<String[]> rows = new ArrayList<>();
        for (int i = offset; i < Math.min(keys.size(), offset + limit); i++) {
            String key = keys.get(i);
            String type = "";
            String value = "";
            try {
                Object t = exec("TYPE", key);
                type = t == null ? "" : String.valueOf(t);
                value = previewValue(type, key);
            } catch (Exception e) {
                value = "<" + e.getMessage() + ">";
            }
            rows.add(new String[]{key, type, value});
        }
        return new QueryResult(new String[]{"key", "type", "value"},
                rows.toArray(new String[0][]), false, System.currentTimeMillis() - t0);
    }

    private String previewValue(String type, String key) throws IOException {
        switch (type) {
            case "string": {
                Object v = exec("GET", key);
                return v == null ? null : String.valueOf(v);
            }
            case "hash": {
                Object v = exec("HGETALL", key);
                return flatten(v, " ");
            }
            case "list": {
                Object v = exec("LRANGE", key, "0", "3");
                return flatten(v, " | ");
            }
            case "set": {
                Object v = exec("SRANDMEMBER", key, "3");
                return flatten(v, " | ");
            }
            case "zset": {
                Object v = exec("ZRANGE", key, "0", "3", "WITHSCORES");
                return flatten(v, " | ");
            }
            default:
                return "";
        }
    }

    private String flatten(Object v, String glue) {
        if (v == null) return null;
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder();
            List<?> list = (List<?>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(glue);
                sb.append(String.valueOf(list.get(i)));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private List<String> scanKeys(int want) throws IOException {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        do {
            Object r = exec("SCAN", cursor, "COUNT", "200");
            if (!(r instanceof List)) break;
            List<?> arr = (List<?>) r;
            if (arr.size() < 2) break;
            cursor = String.valueOf(arr.get(0));
            Object batchObj = arr.get(1);
            if (batchObj instanceof List) {
                for (Object o : (List<?>) batchObj) keys.add(String.valueOf(o));
            }
            if (keys.size() >= want) break;
            if (keys.size() >= MAX_SCAN) break;
        } while (!"0".equals(cursor));
        return keys;
    }

    @Override
    public QueryResult query(String sql, int maxRows) throws Exception {
        long t0 = System.currentTimeMillis();
        String[] argv = splitCommand(sql);
        if (argv.length == 0) return QueryResult.empty();

        Object r = exec(argv);
        List<String[]> rows = new ArrayList<>();
        String[] cols;
        if (r instanceof List) {
            cols = new String[]{"index", "value"};
            int index = 0;
            for (Object o : (List<?>) r) {
                rows.add(new String[]{String.valueOf(index++), o == null ? "(nil)" : String.valueOf(o)});
            }
        } else {
            cols = new String[]{"result"};
            rows.add(new String[]{r == null ? "(nil)" : String.valueOf(r)});
        }
        return new QueryResult(cols, rows.toArray(new String[0][]), false,
                System.currentTimeMillis() - t0);
    }

    /**
     * 命令分词，支持单双引号
     * split a redis command line, quotes aware
     */
    public static String[] splitCommand(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean has = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && i + 1 < line.length()) {
                    cur.append(line.charAt(++i));
                } else {
                    cur.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                has = true;
            } else if (Character.isWhitespace(c)) {
                if (has || cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    has = false;
                }
            } else {
                cur.append(c);
                has = true;
            }
        }
        if (has || cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    @Override
    public int update(String sql) throws Exception {
        query(sql, 1);
        return -1;
    }

    @Override
    public String quote(String identifier) {
        return identifier;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
