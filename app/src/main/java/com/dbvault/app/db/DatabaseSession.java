package com.dbvault.app.db;

import java.io.Closeable;
import java.util.List;

/**
 * 统一的数据库会话。本地 SQLite 与远程 JDBC 都实现这一层，
 * UI 不感知底层差异
 * One session abstraction over local SQLite and remote JDBC;
 * the UI never has to know which one it is talking to.
 */
public interface DatabaseSession extends Closeable {

    /** 展示名，例如 "app.db" 或 "prod@10.0.0.5" */
    String title();

    /** sqlite / mysql / postgresql */
    String dialect();

    boolean isReadOnly();

    /**
     * 列出表与视图
     * List tables and views
     */
    List<DbObject> objects() throws Exception;

    List<ColumnMeta> columns(String table) throws Exception;

    /** 执行任意语句并取前 maxRows 行 */
    QueryResult query(String sql, int maxRows) throws Exception;

    /**
     * 分页浏览一张表
     * Page through a single table
     */
    QueryResult browse(String table, int offset, int limit) throws Exception;

    /**
     * 执行写操作，返回受影响行数，-1 表示未知
     * Run a write statement; returns affected rows, -1 when unknown
     */
    int update(String sql) throws Exception;

    /**
     * 标识符转义，按方言
     * Quote an identifier the way this dialect expects
     */
    String quote(String identifier);

    /**
     * 是否支持标准 SQL 语句
     * whether arbitrary SQL is accepted
     */
    default boolean supportsSql() {
        return true;
    }

    /** 是否支持按绝对行号直接改单元格（非 SQL 引擎用）/ row-addressed cell write */
    default boolean supportsCellUpdate() {
        return false;
    }

    default int updateCellAt(String table, int absoluteRow, String column,
                             String value, boolean isNull) throws Exception {
        throw new UnsupportedOperationException("cell update not supported");
    }

    /**
     * 服务端版本或路径等补充信息
     * short subtitle
     */
    default String subtitle() {
        return title();
    }

    @Override
    void close();
}
