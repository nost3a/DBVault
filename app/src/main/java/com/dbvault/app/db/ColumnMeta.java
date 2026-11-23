package com.dbvault.app.db;

/**
 * 列定义
 * column metadata
 */
public class ColumnMeta {
    public final String name;
    public final String typeName;
    public final boolean nullable;
    public final boolean primaryKey;
    public final int position;

    public ColumnMeta(String name, String typeName, boolean nullable, boolean primaryKey, int position) {
        this.name = name;
        this.typeName = typeName == null ? "" : typeName;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.position = position;
    }

    public String shortType() {
        if (typeName.isEmpty()) return "";
        int cut = typeName.indexOf('(');
        String t = cut > 0 ? typeName.substring(0, cut) : typeName;
        return t.toLowerCase();
    }
}
