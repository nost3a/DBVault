package com.dbvault.app.db;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 连接配置的本地存储
 * persistent list of saved connections
 */
public class ConnectionStore {

    private static final String PREF = "dbvault.connections";
    private static final String KEY = "items";

    private final SharedPreferences sp;

    public ConnectionStore(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public List<ConnectionProfile> load() {
        String raw = sp.getString(KEY, "[]");
        List<ConnectionProfile> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ConnectionProfile p = ConnectionProfile.fromJson(o);
                p.password = SecretBox.open(o.optString("password", ""));
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        return out;
    }

    public void saveAll(List<ConnectionProfile> items) {
        JSONArray arr = new JSONArray();
        for (ConnectionProfile p : items) {
            try {
                JSONObject o = p.toJson();
                o.put("password", SecretBox.seal(p.password));
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString(KEY, arr.toString()).apply();
    }

    public void upsert(ConnectionProfile profile) {
        List<ConnectionProfile> all = load();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(profile.id)) {
                all.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(profile);
        saveAll(all);
    }

    public void remove(String id) {
        List<ConnectionProfile> all = load();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) all.remove(i);
        }
        saveAll(all);
    }
}
