package com.dbvault.app.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 SQLite 会话，直接走系统自带的 sqlite3 实现，不依赖额外 native 库。
 * Local SQLite session built on the platform engine, no bundled natives.
 */
public class SqliteSession implements DatabaseSession {

    private final File file;
    private final SQLiteDatabase db;
    private final boolean readOnly;

    public SqliteSession(File file) throws Exception {
        this.file = file;
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在 / file not found: " + file.getName());
        }

        boolean preferReadOnly = !file.canWrite();
        SQLiteDatabase opened;
        try {
            opened = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    preferReadOnly ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE);
        } catch (SQLiteException e) {
            // 目录不可写、WAL 残留等场景下退回只读
            // falls back to read-only when the directory or a leftover WAL blocks writes
            // fall back to read-only
            opened = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            preferReadOnly = true;
        }
        this.db = opened;
        this.readOnly = preferReadOnly;
    }

    @Override
    public String title() {
        return file.getName();
    }

    @Override
    public String dialect() {
        return "sqlite";
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public List<DbObject> objects() {
        List<DbObject> out = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT name, type FROM sqlite_master "
                        + "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' "
                        + "ORDER BY type, name", null);
        try {
            while (c.moveToNext()) {
                String name = c.getString(0);
                String type = c.getString(1);
                boolean view = "view".equals(type);
                long rows = view ? -1 : safeCount(name);
                out.add(new DbObject(name, view ? DbObject.TYPE_VIEW : DbObject.TYPE_TABLE, rows));
            }
        } finally {
            c.close();
        }
        return out;
    }

    private long safeCount(String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM " + quote(table), null);
            return c.moveToFirst() ? c.getLong(0) : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.close();
        }
    }

    @Override
    public List<ColumnMeta> columns(String table) {
        List<ColumnMeta> out = new ArrayList<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + quote(table) + ")", null);
        try {
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                String type = c.getString(c.getColumnIndexOrThrow("type"));
                boolean notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) != 0;
                int pk = c.getInt(c.getColumnIndexOrThrow("pk"));
                int pos = c.getInt(c.getColumnIndexOrThrow("cid"));
                out.add(new ColumnMeta(name, type, !notNull, pk > 0, pos));
            }
        } finally {
            c.close();
        }
        return out;
    }

    @Override
    public QueryResult query(String sql, int maxRows) {
        long t0 = System.currentTimeMillis();
        if (!isReadStatement(sql)) {
            update(sql);
            return new QueryResult(new String[]{"状态"}, new String[][]{{"语句已执行"}}, false,
                    System.currentTimeMillis() - t0);
        }
        Cursor c = db.rawQuery(sql, null);
        try {
            String[] cols = c.getColumnNames();
            List<String[]> rows = new ArrayList<>();
            boolean truncated = false;
            while (c.moveToNext()) {
                if (rows.size() >= maxRows) {
                    truncated = true;
                    break;
                }
                String[] row = new String[cols.length];
                for (int i = 0; i < cols.length; i++) {
                    row[i] = cursorValue(c, i);
                }
                rows.add(row);
            }
            return new QueryResult(cols, rows.toArray(new String[0][]), truncated,
                    System.currentTimeMillis() - t0);
        } finally {
            c.close();
        }
    }

    private String cursorValue(Cursor c, int index) {
        switch (c.getType(index)) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_BLOB: {
                byte[] b = c.getBlob(index);
                if (b == null) return null;
                StringBuilder sb = new StringBuilder("0x");
                int n = Math.min(b.length, 24);
                for (int i = 0; i < n; i++) sb.append(String.format("%02X", b[i]));
                if (b.length > n) sb.append("… (").append(b.length).append("B)");
                return sb.toString();
            }
            case Cursor.FIELD_TYPE_INTEGER:
            case Cursor.FIELD_TYPE_FLOAT:
            default:
                return c.getString(index);
        }
    }

    private static boolean isReadStatement(String sql) {
        String s = sql.trim().toUpperCase(java.util.Locale.ROOT);
        return s.startsWith("SELECT") || s.startsWith("PRAGMA") || s.startsWith("EXPLAIN")
                || s.startsWith("WITH") || s.startsWith("SELECT");
    }

    @Override
    public QueryResult browse(String table, int offset, int limit) {
        return query(Sql.pageQuery(dialect(), quote(table), offset, limit), limit + 1);
    }

    @Override
    public int update(String sql) {
        db.execSQL(sql);
        return -1;
    }

    @Override
    public String quote(String identifier) {
        return Sql.quote("sqlite", identifier);
    }

    /**
     * 把 WAL 合并回主文件，便于把改动同步回原位置
     * fold the WAL back before copying
     */
    public void flush() {
        try {
            android.database.Cursor c = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            c.moveToFirst();
            c.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        try {
            flush();
            db.close();
        } catch (Exception ignored) {
        }
    }
}
