package com.dbvault.app.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询结果快照
 * an immutable snapshot of a query result
 */
public class QueryResult {
    public final String[] columns;
    public final String[][] rows;
    public final boolean truncated;
    public final long elapsedMs;

    public QueryResult(String[] columns, String[][] rows, boolean truncated, long elapsedMs) {
        this.columns = columns;
        this.rows = rows;
        this.truncated = truncated;
        this.elapsedMs = elapsedMs;
    }

    public static QueryResult empty() {
        return new QueryResult(new String[0], new String[0][], false, 0);
    }

    public int columnCount() {
        return columns.length;
    }

    public int rowCount() {
        return rows.length;
    }

    public List<String> columnList() {
        List<String> out = new ArrayList<>(columns.length);
        for (String c : columns) out.add(c);
        return out;
    }
}
