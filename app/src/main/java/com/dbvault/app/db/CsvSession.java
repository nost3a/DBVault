package com.dbvault.app.db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV/TSV 会话。整表读进内存，写入时按 RFC4180 重新序列化落盘。
 * CSV session: full table in memory, re-serialised on write.
 */
public class CsvSession implements DatabaseSession {

    private static final Charset CS = Charset.forName("UTF-8");

    private final File file;
    private final char sep;
    private final String tableName;
    private String[] header;
    private final List<String[]> rows = new ArrayList<>();

    public CsvSession(File file) throws Exception {
        this.file = file;
        this.sep = file.getName().toLowerCase().endsWith(".tsv") ? '\t' : ',';
        this.tableName = stripExt(file.getName());
        load();
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private void load() throws Exception {
        byte[] raw;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raw = new byte[(int) raf.length()];
            raf.readFully(raw);
        }
        String text = new String(raw, CS);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        rows.clear();
        List<String[]> parsed = parse(text);
        if (parsed.isEmpty()) {
            header = new String[]{"value"};
            return;
        }
        header = parsed.get(0);
        for (int i = 1; i < parsed.size(); i++) {
            String[] r = parsed.get(i);
            if (r.length == 1 && r[0].isEmpty()) continue;
            String[] fixed = new String[header.length];
            for (int c = 0; c < header.length; c++) fixed[c] = c < r.length ? r[c] : "";
            rows.add(fixed);
        }
    }

    private List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<>();
        List<String> line = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                line.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                line.add(cell.toString());
                cell.setLength(0);
                out.add(line.toArray(new String[0]));
                line = new ArrayList<>();
            } else if (c == '\r') {
                // 交给 \n 处理
                // dropped here, the line is committed by the \n branch
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !line.isEmpty()) {
            line.add(cell.toString());
            out.add(line.toArray(new String[0]));
        }
        return out;
    }

    @Override
    public String title() {
        return file.getName();
    }

    @Override
    public String dialect() {
        return sep == '\t' ? "tsv" : "csv";
    }

    @Override
    public boolean isReadOnly() {
        return !file.canWrite();
    }

    @Override
    public boolean supportsSql() {
        return false;
    }

    @Override
    public boolean supportsCellUpdate() {
        return !isReadOnly();
    }

    @Override
    public List<DbObject> objects() {
        List<DbObject> out = new ArrayList<>();
        out.add(new DbObject(tableName, DbObject.TYPE_TABLE, rows.size()));
        return out;
    }

    @Override
    public List<ColumnMeta> columns(String table) {
        List<ColumnMeta> out = new ArrayList<>();
        for (int i = 0; i < header.length; i++) {
            out.add(new ColumnMeta(header[i], "text", true, false, i));
        }
        return out;
    }

    @Override
    public QueryResult query(String sql, int maxRows) {
        throw new UnsupportedOperationException(
                "CSV 不支持 SQL 语句，请直接打开表 / SQL is not available for csv files");
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) {
        List<String[]> slice = new ArrayList<>();
        for (int i = offset; i < Math.min(rows.size(), offset + limit); i++) slice.add(rows.get(i));
        return new QueryResult(header, slice.toArray(new String[0][]), false, 0);
    }

    @Override
    public int update(String sql) {
        throw new UnsupportedOperationException("CSV 只能按单元格写入 / cell editing only");
    }

    @Override
    public int updateCellAt(String table, int absoluteRow, String column,
                            String value, boolean isNull) throws Exception {
        int col = -1;
        for (int i = 0; i < header.length; i++) {
            if (header[i].equalsIgnoreCase(column)) {
                col = i;
                break;
            }
        }
        if (col < 0) throw new IllegalArgumentException("unknown column: " + column);
        if (absoluteRow < 0 || absoluteRow >= rows.size()) {
            throw new IllegalArgumentException("row out of range");
        }
        rows.get(absoluteRow)[col] = isNull ? "" : value;
        save();
        return 1;
    }

    private void save() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(join(header)).append('\n');
        for (String[] r : rows) sb.append(join(r)).append('\n');
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(sb.toString().getBytes(CS));
            out.flush();
        }
    }

    private String join(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(sep);
            String v = row[i] == null ? "" : row[i];
            if (v.indexOf(sep) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    @Override
    public String quote(String identifier) {
        return identifier;
    }

    @Override
    public void close() {
    }
}
