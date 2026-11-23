package com.dbvault.app.db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * dBASE（.dbf）会话。定长记录结构，直接按字节偏移读写，不需要额外依赖。
 * dBASE session: fixed-length records edited byte by byte.
 */
public class DbfSession implements DatabaseSession {

    private static final Charset CS = Charset.forName("GBK");

    private static class Field {
        String name;
        char type;
        int offset;
        int length;
        int decimals;
    }

    private final File file;
    private byte[] data;
    private int recordCount;
    private int headerLen;
    private int recordLen;
    private final List<Field> fields = new ArrayList<>();
    private final String tableName;

    public DbfSession(File file) throws Exception {
        this.file = file;
        this.tableName = stripExt(file.getName());
        this.data = readAll(file);
        if (data.length < 32) throw new IllegalArgumentException("not a dbf file");
        recordCount = le32(4);
        headerLen = le16(8);
        recordLen = le16(10);
        parseFields();
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static byte[] readAll(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return b;
        }
    }

    private void parseFields() {
        int p = 32;
        int offset = 1;
        while (p + 32 <= data.length && data[p] != 0x0D) {
            Field f = new Field();
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 11; i++) {
                char c = (char) (data[p + i] & 0xFF);
                if (c == 0 || c == ' ') break;
                name.append(c);
            }
            f.name = name.toString();
            f.type = (char) (data[p + 11] & 0xFF);
            f.length = data[p + 16] & 0xFF;
            f.decimals = data[p + 17] & 0xFF;
            f.offset = offset;
            offset += f.length;
            fields.add(f);
            p += 32;
        }
        if (recordLen <= 0) recordLen = offset;
    }

    private int le16(int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
    }

    private int le32(int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8)
                | ((data[at + 2] & 0xFF) << 16) | ((data[at + 3] & 0xFF) << 24);
    }

    @Override
    public String title() {
        return file.getName();
    }

    @Override
    public String dialect() {
        return "dbf";
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
        out.add(new DbObject(tableName, DbObject.TYPE_TABLE, recordCount));
        return out;
    }

    @Override
    public List<ColumnMeta> columns(String table) {
        List<ColumnMeta> out = new ArrayList<>();
        int pos = 0;
        for (Field f : fields) {
            out.add(new ColumnMeta(f.name, typeName(f), true, false, pos++));
        }
        return out;
    }

    private static String typeName(Field f) {
        switch (f.type) {
            case 'C':
                return "char(" + f.length + ")";
            case 'N':
                return "numeric(" + f.length + "," + f.decimals + ")";
            case 'F':
                return "float(" + f.length + "," + f.decimals + ")";
            case 'D':
                return "date";
            case 'L':
                return "logical";
            case 'I':
                return "int";
            case 'M':
                return "memo";
            default:
                return "raw(" + f.length + ")";
        }
    }

    @Override
    public QueryResult query(String sql, int maxRows) {
        throw new UnsupportedOperationException(
                "dBASE 不支持 SQL 语句，请直接打开表 / SQL is not available for dbf files");
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) {
        long t0 = System.currentTimeMillis();
        String[] cols = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) cols[i] = fields.get(i).name;

        List<String[]> rows = new ArrayList<>();
        for (int r = offset; r < Math.min(recordCount, offset + limit); r++) {
            if (isDeleted(r)) continue;
            String[] line = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) line[i] = readField(r, fields.get(i));
            rows.add(line);
        }
        return new QueryResult(cols, rows.toArray(new String[0][]), false,
                System.currentTimeMillis() - t0);
    }

    private boolean isDeleted(int row) {
        int at = headerLen + row * recordLen;
        return at < data.length && data[at] == '*';
    }

    private int fieldPos(int row, Field f) {
        return headerLen + row * recordLen + f.offset;
    }

    private String readField(int row, Field f) {
        int at = fieldPos(row, f);
        if (at < 0 || at + f.length > data.length) return null;
        switch (f.type) {
            case 'D': {
                String raw = new String(data, at, f.length, CS).trim();
                if (raw.length() != 8) return raw.isEmpty() ? null : raw;
                return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
            }
            case 'L': {
                char c = (char) (data[at] & 0xFF);
                if (c == 'T' || c == 'Y' || c == 't' || c == 'y') return "true";
                if (c == 'F' || c == 'N' || c == 'f' || c == 'n') return "false";
                return null;
            }
            case 'I': {
                int v = (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8)
                        | ((data[at + 2] & 0xFF) << 16) | ((data[at + 3] & 0xFF) << 24);
                return String.valueOf(v);
            }
            case 'M':
                return "(memo)";
            default: {
                String raw = new String(data, at, f.length, CS);
                raw = raw.replace('\0', ' ').trim();
                return raw.isEmpty() ? null : raw;
            }
        }
    }

    @Override
    public int update(String sql) {
        throw new UnsupportedOperationException("dBASE 只能按单元格写入 / cell editing only");
    }

    @Override
    public int updateCellAt(String table, int absoluteRow, String column,
                            String value, boolean isNull) throws Exception {
        int idx = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name.equalsIgnoreCase(column)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) throw new IllegalArgumentException("unknown field: " + column);
        if (absoluteRow < 0 || absoluteRow >= recordCount) {
            throw new IllegalArgumentException("row out of range");
        }
        encode(absoluteRow, fields.get(idx), isNull ? null : value);
        save();
        return 1;
    }

    private void encode(int row, Field f, String value) throws Exception {
        int at = fieldPos(row, f);
        if (at + f.length > data.length) throw new IllegalStateException("offset beyond file");

        if (value == null) {
            for (int i = 0; i < f.length; i++) data[at + i] = (byte) (f.type == 'C' ? ' ' : ' ');
            return;
        }

        switch (f.type) {
            case 'D': {
                String d = value.replace("-", "").replace("/", "").trim();
                byte[] b = padLeft(d, f.length);
                System.arraycopy(b, 0, data, at, f.length);
                break;
            }
            case 'L': {
                char c = ("1".equals(value) || "true".equalsIgnoreCase(value)
                        || "y".equalsIgnoreCase(value)) ? 'T' : 'F';
                data[at] = (byte) c;
                break;
            }
            case 'I': {
                int v = Integer.parseInt(value.trim());
                data[at] = (byte) (v & 0xFF);
                data[at + 1] = (byte) ((v >> 8) & 0xFF);
                data[at + 2] = (byte) ((v >> 16) & 0xFF);
                data[at + 3] = (byte) ((v >> 24) & 0xFF);
                break;
            }
            case 'N':
            case 'F': {
                byte[] b = padLeft(value.trim(), f.length);
                System.arraycopy(b, 0, data, at, f.length);
                break;
            }
            default: {
                byte[] b = padRight(value, f.length);
                System.arraycopy(b, 0, data, at, f.length);
                break;
            }
        }
    }

    private static byte[] padLeft(String s, int len) {
        byte[] raw = s.getBytes(CS);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = ' ';
        int copy = Math.min(raw.length, len);
        System.arraycopy(raw, 0, out, len - copy, copy);
        return out;
    }

    private static byte[] padRight(String s, int len) {
        byte[] raw = s.getBytes(CS);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = ' ';
        int copy = Math.min(raw.length, len);
        System.arraycopy(raw, 0, out, 0, copy);
        return out;
    }

    private void save() throws Exception {
        java.util.Calendar c = java.util.Calendar.getInstance();
        data[1] = (byte) (c.get(java.util.Calendar.YEAR) - 1900);
        data[2] = (byte) (c.get(java.util.Calendar.MONTH) + 1);
        data[3] = (byte) c.get(java.util.Calendar.DAY_OF_MONTH);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
        }
    }

    @Override
    public String quote(String identifier) {
        return identifier;
    }

    @Override
    public void close() {
        data = null;
    }
}
