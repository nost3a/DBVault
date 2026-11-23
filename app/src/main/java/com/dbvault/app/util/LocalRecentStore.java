package com.dbvault.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 最近打开过的本地库文件
 * recently opened local database files
 */
public class LocalRecentStore {

    public static class Entry {
        public String uri;
        public String name;
        public long size;
        public long lastUsed;
        /** sqlite 或 script */
        public String kind;
    }

    private static final String PREF = "dbvault.local";
    private static final String KEY = "recent";
    private static final int MAX = 12;

    private final SharedPreferences sp;

    public LocalRecentStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.uri = o.optString("uri");
                e.name = o.optString("name");
                e.size = o.optLong("size");
                e.lastUsed = o.optLong("lastUsed");
                e.kind = o.optString("kind", "sqlite");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.lastUsed).reversed());
        return out;
    }

    public void touch(String uri, String name, long size, String kind) {
        List<Entry> all = load();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).uri.equals(uri)) all.remove(i);
        }
        Entry e = new Entry();
        e.uri = uri;
        e.name = name;
        e.size = size;
        e.lastUsed = System.currentTimeMillis();
        e.kind = kind;
        all.add(0, e);
        while (all.size() > MAX) all.remove(all.size() - 1);
        JSONArray arr = new JSONArray();
        for (Entry x : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("uri", x.uri);
                o.put("name", x.name);
                o.put("size", x.size);
                o.put("lastUsed", x.lastUsed);
                o.put("kind", x.kind);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        sp.edit().putString(KEY, arr.toString()).apply();
    }

    public void forget(String uri) {
        List<Entry> all = load();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).uri.equals(uri)) all.remove(i);
        }
        JSONArray arr = new JSONArray();
        for (Entry x : all) {
            JSONObject o = new JSONObject();
            try {
                o.put("uri", x.uri);
                o.put("name", x.name);
                o.put("size", x.size);
                o.put("lastUsed", x.lastUsed);
                o.put("kind", x.kind);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        sp.edit().putString(KEY, arr.toString()).apply();
    }
}
