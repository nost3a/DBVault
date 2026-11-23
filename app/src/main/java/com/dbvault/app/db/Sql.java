package com.dbvault.app.db;

import java.util.Locale;

/**
 * SQL 方言相关的小工具
 * dialect helpers
 */
public final class Sql {

    private Sql() {
    }

    public static String quote(String dialect, String id) {
        if (id == null) return "?";
        switch (dialect) {
            case "mysql":
                return "`" + id.replace("`", "``") + "`";
            case "mssql":
                return "[" + id.replace("]", "]]") + "]";
            case "postgresql":
            case "sqlite":
            default:
                return "\"" + id.replace("\"", "\"\"") + "\"";
        }
    }

    /**
     * 生成安全的字符串字面量
     * build a quoted string literal
     */
    public static String literal(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("'", "''") + "'";
    }

    public static String pageQuery(String dialect, String tableRef, int offset, int limit) {
        int skip = Math.max(0, offset);
        if ("mssql".equals(dialect)) {
            if (skip == 0) return "SELECT TOP (" + limit + ") * FROM " + tableRef;
            return "SELECT * FROM " + tableRef
                    + " ORDER BY (SELECT NULL) OFFSET " + skip + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }
        return "SELECT * FROM " + tableRef + " LIMIT " + limit + " OFFSET " + skip;
    }

    public static String likeOperator(String dialect) {
        return "LIKE";
    }

    public static boolean supportsLimit(String dialect) {
        return true;
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
