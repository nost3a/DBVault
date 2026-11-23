package com.dbvault.app.db;

/**
 * 一个库对象：表或视图
 * a table or view inside a database
 */
public class DbObject {
    public static final int TYPE_TABLE = 1;
    public static final int TYPE_VIEW = 2;

    public final String name;
    public final int type;
    /**
     * 行数，-1 表示未知
     * row count, -1 when unknown
     */
    public final long rowCount;

    public DbObject(String name, int type, long rowCount) {
        this.name = name;
        this.type = type;
        this.rowCount = rowCount;
    }

    public boolean isView() {
        return type == TYPE_VIEW;
    }
}
