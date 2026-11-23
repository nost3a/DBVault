package com.dbvault.app.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * 一条远程连接配置
 * a saved remote connection
 */
public class ConnectionProfile {

    public static final String TYPE_MYSQL = "mysql";
    public static final String TYPE_POSTGRES = "postgresql";
    public static final String TYPE_MSSQL = "mssql";
    public static final String TYPE_REDIS = "redis";

    public String id;
    public String label;
    public String type;
    public String host;
    public int port;
    public String user;
    public String password;
    public String database;
    public String schema;
    public boolean ssl;
    public long lastUsed;

    public ConnectionProfile() {
        this.id = UUID.randomUUID().toString();
        this.type = TYPE_MYSQL;
        this.host = "";
        this.port = 3306;
        this.user = "";
        this.password = "";
        this.database = "";
        this.schema = "";
        this.label = "";
    }

    public String displayName() {
        if (label != null && !label.trim().isEmpty()) return label.trim();
        String db = database == null || database.isEmpty() ? "(default)" : database;
        return db + " @ " + host;
    }

    public static int defaultPort(String type) {
        if (TYPE_POSTGRES.equals(type)) return 5432;
        if (TYPE_MSSQL.equals(type)) return 1433;
        if (TYPE_REDIS.equals(type)) return 6379;
        return 3306;
    }

    public String typeLabel() {
        if (TYPE_POSTGRES.equals(type)) return "PostgreSQL";
        if (TYPE_MSSQL.equals(type)) return "SQL Server";
        if (TYPE_REDIS.equals(type)) return "Redis";
        return "MySQL / MariaDB";
    }

    public String subtitle() {
        String t = typeLabel();
        String db = database == null || database.isEmpty() ? "" : database;
        return t + " · " + host + ":" + port + (db.isEmpty() ? "" : " / " + db);
    }

    public ConnectionProfile copy() {
        ConnectionProfile p = new ConnectionProfile();
        p.id = id;
        p.label = label;
        p.type = type;
        p.host = host;
        p.port = port;
        p.user = user;
        p.password = password;
        p.database = database;
        p.schema = schema;
        p.ssl = ssl;
        p.lastUsed = lastUsed;
        return p;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label);
        o.put("type", type);
        o.put("host", host);
        o.put("port", port);
        o.put("user", user);
        o.put("password", password);
        o.put("database", database);
        o.put("schema", schema);
        o.put("ssl", ssl);
        o.put("lastUsed", lastUsed);
        return o;
    }

    public static ConnectionProfile fromJson(JSONObject o) {
        ConnectionProfile p = new ConnectionProfile();
        p.id = o.optString("id", p.id);
        p.label = o.optString("label", "");
        p.type = o.optString("type", TYPE_MYSQL);
        p.host = o.optString("host", "");
        p.port = o.optInt("port", defaultPort(p.type));
        p.user = o.optString("user", "");
        p.password = o.optString("password", "");
        p.database = o.optString("database", "");
        p.schema = o.optString("schema", "");
        p.ssl = o.optBoolean("ssl", false);
        p.lastUsed = o.optLong("lastUsed", 0);
        return p;
    }
}
