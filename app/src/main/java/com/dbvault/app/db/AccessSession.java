package com.dbvault.app.db;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Index;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Access（.mdb/.accdb）会话。Access 没有标准 SQL 接口，这里直接用 Jackcess 摸表读写，
 * SQL 控制台在该会话下不可用。
 * Access (.mdb/.accdb) session. There is no SQL dialect here, so tables are read and
 * written through Jackcess; the SQL console stays disabled.
 */
public class AccessSession implements DatabaseSession {

    private final File file;
    private final Database db;

    public AccessSession(File file) throws Exception {
        this.file = file;
        this.db = new DatabaseBuilder(file)
                .setReadOnly(!file.canWrite())
                .setAutoSync(false)
                .open();
    }

    @Override
    public String title() {
        return file.getName();
    }

    @Override
    public String dialect() {
        return "access";
    }

    @Override
    public String subtitle() {
        return file.getName() + (isReadOnly() ? " · read-only" : "");
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
    public List<DbObject> objects() throws Exception {
        List<DbObject> out = new ArrayList<>();
        for (String n : db.getTableNames()) {
            long rows = -1;
            try {
                rows = db.getTable(n).getRowCount();
            } catch (Exception ignored) {
            }
            out.add(new DbObject(n, DbObject.TYPE_TABLE, rows));
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    @Override
    public List<ColumnMeta> columns(String table) throws Exception {
        Table t = db.getTable(table);
        List<String> pk = primaryKeyNames(t);
        List<ColumnMeta> out = new ArrayList<>();
        int pos = 0;
        for (Column c : t.getColumns()) {
            out.add(new ColumnMeta(c.getName(), String.valueOf(c.getType()),
                    true, pk.contains(c.getName()), pos++));
        }
        return out;
    }

    private List<String> primaryKeyNames(Table t) {
        List<String> out = new ArrayList<>();
        try {
            Index idx = t.getPrimaryKeyIndex();
            if (idx != null) {
                for (Index.Column c : idx.getColumns()) out.add(c.getName());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    public QueryResult query(String sql, int maxRows) {
        throw new UnsupportedOperationException(
                "Access 不支持 SQL 语句，请直接打开表");
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) throws Exception {
        long t0 = System.currentTimeMillis();
        Table t = db.getTable(table);
        List<? extends Column> cols = t.getColumns();

        String[] names = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) names[i] = cols.get(i).getName();

        List<String[]> rows = new ArrayList<>();
        Cursor cur = t.getDefaultCursor();
        try {
            cur.reset();
            int index = -1;
            while (cur.moveToNextRow()) {
                index++;
                if (index < offset) continue;
                if (rows.size() >= limit) break;
                Row row = cur.getCurrentRow();
                String[] line = new String[names.length];
                for (int i = 0; i < names.length; i++) line[i] = stringify(row.get(names[i]));
                rows.add(line);
            }
        } finally {
            try {
                cur.reset();
            } catch (Exception ignored) {
            }
        }
        return new QueryResult(names, rows.toArray(new String[0][]), false,
                System.currentTimeMillis() - t0);
    }

    private static String stringify(Object o) {
        if (o == null) return null;
        if (o instanceof byte[]) {
            byte[] b = (byte[]) o;
            StringBuilder sb = new StringBuilder("0x");
            int n = Math.min(b.length, 24);
            for (int i = 0; i < n; i++) sb.append(String.format("%02X", b[i]));
            if (b.length > n) sb.append("…(").append(b.length).append("B)");
            return sb.toString();
        }
        if (o instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .format((java.util.Date) o);
        }
        return String.valueOf(o);
    }

    @Override
    public int update(String sql) {
        throw new UnsupportedOperationException("Access 不支持 SQL 写入，按单元格改");
    }

    @Override
    public int updateCellAt(String table, int absoluteRow, String column,
                            String value, boolean isNull) throws Exception {
        Table t = db.getTable(table);
        List<? extends Column> cols = t.getColumns();

        int target = -1;
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).getName().equalsIgnoreCase(column)) {
                target = i;
                break;
            }
        }
        if (target < 0) throw new IllegalArgumentException("unknown column: " + column);

        Cursor cur = t.getDefaultCursor();
        cur.reset();
        Row found = null;
        int index = -1;
        while (cur.moveToNextRow()) {
            index++;
            if (index == absoluteRow) {
                found = cur.getCurrentRow();
                break;
            }
        }
        if (found == null) throw new IllegalStateException("row " + absoluteRow + " not found");

        Object[] values = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) values[i] = found.get(cols.get(i).getName());
        values[target] = isNull ? null : coerce(cols.get(target).getType(), value);

        cur.updateCurrentRow(values);
        cur.reset();
        return 1;
    }

    private static Object coerce(DataType type, String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        try {
            switch (type) {
                case BOOLEAN:
                    return "1".equals(v) || "true".equalsIgnoreCase(v) || "y".equalsIgnoreCase(v);
                case BYTE:
                    return (byte) Integer.parseInt(v);
                case INT:
                case LONG:
                    return Long.parseLong(v);
                case DOUBLE:
                case FLOAT:
                case NUMERIC:
                case MONEY:
                    return Double.parseDouble(v);
                default:
                    return raw;
            }
        } catch (Exception e) {
            return raw;
        }
    }

    @Override
    public String quote(String identifier) {
        return "[" + identifier + "]";
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (Exception ignored) {
        }
    }
}
