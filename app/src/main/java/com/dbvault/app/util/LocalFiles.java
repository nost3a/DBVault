package com.dbvault.app.util;

import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.dbvault.app.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 本地文件相关的小操作
 * local file plumbing
 */
public final class LocalFiles {

    private LocalFiles() {
    }

    public static String displayName(Context ctx, Uri uri) {
        String name = null;
        try (android.database.Cursor c = ctx.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            String last = uri.getLastPathSegment();
            name = last == null ? "database.db" : last;
        }
        return name;
    }

    public static long size(Context ctx, Uri uri) {
        try (android.database.Cursor c = ctx.getContentResolver()
                .query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * 把选中的文件复制到应用私有目录，便于 SQLite 以读写模式打开
     * mirror into app storage
     */
    public static File mirror(Context ctx, Uri uri, String name) throws Exception {
        File dir = new File(ctx.getFilesDir(), "mirror");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录 / cannot create dir");
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
        File out = new File(dir, safe);

        // 同名但来源不同的文件，用 uri 哈希区分
        // same file name from another source is kept apart by a hash of the uri
        // avoid clobbering different sources
        File sidecar = new File(dir, safe + ".src");
        String tag = uri.toString();
        if (out.exists() && sidecar.exists()) {
            String old = readText(sidecar);
            if (!tag.equals(old)) {
                out = new File(dir, Integer.toHexString(tag.hashCode()) + "_" + safe);
            }
        }

        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream fos = new FileOutputStream(out, false)) {
            if (in == null) throw new IOException("无法读取该文件 / cannot open source");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        writeText(sidecar, tag);
        return out;
    }

    /**
     * 复制前先把 WAL 落盘，避免只拷主文件丢数据
     * checkpoint before copying
     */
    public static void syncBack(File mirrored, String uriString) throws Exception {
        Uri uri = Uri.parse(uriString);
        try (InputStream in = new FileInputStream(mirrored);
             OutputStream out = App.get().getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("目标文件不可写 / target not writable");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    public static boolean isSqlite(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] head = new byte[16];
            int n = in.read(head);
            if (n < 16) return false;
            String magic = new String(head, java.nio.charset.StandardCharsets.UTF_8);
            return magic.startsWith("SQLite format 3");
        }
    }

    public static String readText(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] all = new byte[(int) Math.min(f.length(), 1 << 20)];
            int off = 0;
            int n;
            while (off < all.length && (n = in.read(all, off, all.length - off)) > 0) off += n;
            return new String(all, 0, off, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File f, String s) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f, false)) {
            fos.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }


    /**
     * 认一下文件头，给用户一个明确的结论
     * sniff the header and name the format
     */
    public static String detectFormat(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] head = new byte[32];
            int n = in.read(head);
            if (n < 4) return "空文件或无法读取";
            String ascii = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (ascii.startsWith("SQLite format 3")) return "SQLite";
            if (ascii.contains("Standard Jet DB")) return "Access 97-2003 (.mdb) — 需要 Access 引擎，手机端无法直接打开";
            if (ascii.contains("Standard ACE DB")) return "Access 2007+ (.accdb) — 同上";
            if (head[0] == 0x03 || head[0] == 0x30 || head[0] == (byte) 0x83) {
                if (f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".dbf")) return "dBASE / FoxPro (.dbf) — 支持有限";
            }
            if (head[0] == 0x50 && head[1] == 0x4B) return "ZIP 容器（可能是 .xlsx / .apks / 打包文件）";
            if (head[0] == 0x1F && head[1] == (byte) 0x8B) return "GZIP 压缩文件";
            return "未知格式，首字节 " + String.format("%02X %02X %02X %02X", head[0], head[1], head[2], head[3]);
        } catch (Exception e) {
            return "无法读取文件头";
        }
    }

    public static String guessKind(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".sql") || lower.endsWith(".txt")) return "script";
        return "sqlite";
    }

    public static void keepPermission(Context ctx, Uri uri) {
        try {
            ctx.getContentResolver().takePersistableUriPermission(uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    public static boolean hasWrite(Context ctx, Uri uri) {
        for (UriPermission p : ctx.getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isWritePermission()) return true;
        }
        return false;
    }
}
