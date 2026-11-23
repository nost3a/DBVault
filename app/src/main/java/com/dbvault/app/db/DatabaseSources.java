package com.dbvault.app.db;

import android.content.Context;

import com.dbvault.app.R;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Locale;

/**
 * 会话工厂：按文件格式或连接类型挑选实现。
 * Session factory that picks an implementation from the file format or the profile type.
 */
public final class DatabaseSources {

    private DatabaseSources() {
    }

    public static DatabaseSession localOpen(File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".mdb") || name.endsWith(".accdb")) {
            return new AccessSession(file);
        }
        if (name.endsWith(".dbf")) {
            return new DbfSession(file);
        }
        if (name.endsWith(".csv") || name.endsWith(".tsv")) {
            return new CsvSession(file);
        }
        if (isSqlite(file)) {
            return new SqliteSession(file);
        }
        throw new IllegalArgumentException("unsupported-file:" + file.getName());
    }

    public static boolean isSqlite(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 16) return false;
            byte[] head = new byte[16];
            raf.readFully(head);
            String s = new String(head, "UTF-8");
            return s.startsWith("SQLite format 3");
        } catch (Exception e) {
            return false;
        }
    }

    public static DatabaseSession remoteOpen(ConnectionProfile p) throws Exception {
        if (ConnectionProfile.TYPE_REDIS.equals(p.type)) {
            return new RedisSession(p);
        }
        return new JdbcSession(p);
    }

    /**
     * 把异常整理成能看懂的一句话
     * turn the throwable into a readable line
     */
    public static String describe(Context ctx, Throwable e) {
        if (e == null) return "unknown error";
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();

        String msg = root.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = root.getClass().getSimpleName();

        if (msg.startsWith("unsupported-file:")) {
            return ctx.getString(R.string.err_unknown_format,
                    msg.substring("unsupported-file:".length()));
        }

        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("refused")) return ctx.getString(R.string.err_refused, msg);
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return ctx.getString(R.string.err_timeout, msg);
        }
        if (lower.contains("access denied") || lower.contains("authentication")) {
            return ctx.getString(R.string.err_auth, msg);
        }
        if (lower.contains("unknown database")) return ctx.getString(R.string.err_unknown_db, msg);
        if (lower.contains("no such table")) return ctx.getString(R.string.err_no_table, msg);
        if (lower.contains("unable to resolve host") || lower.contains("unknownhost")) {
            return ctx.getString(R.string.err_dns, msg);
        }
        if (lower.contains("classnotfound") || lower.contains("noclassdeffound")) {
            return ctx.getString(R.string.err_driver, msg);
        }
        return msg;
    }
}
