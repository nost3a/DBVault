package com.dbvault.app.util;

import com.dbvault.app.db.DatabaseSession;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 打开的会话表
 * Activity 之间只传 id，避免序列化连接对象。
 * Live session handles addressable across activities; only ids travel between screens,
 * so connection objects are never serialised.
 */
public final class Sessions {

    public static class Handle {
        public final DatabaseSession session;
        public final String sourceUri;
        public final File mirroredFile;

        Handle(DatabaseSession session, String sourceUri, File mirroredFile) {
            this.session = session;
            this.sourceUri = sourceUri;
            this.mirroredFile = mirroredFile;
        }
    }

    private static final Map<String, Handle> OPEN = new HashMap<>();

    private Sessions() {
    }

    public static synchronized String attach(DatabaseSession session) {
        return attach(session, null, null);
    }

    public static synchronized String attach(DatabaseSession session, String sourceUri, File mirrored) {
        String id = UUID.randomUUID().toString();
        OPEN.put(id, new Handle(session, sourceUri, mirrored));
        return id;
    }

    public static synchronized Handle handle(String id) {
        return OPEN.get(id);
    }

    public static synchronized DatabaseSession get(String id) {
        Handle h = OPEN.get(id);
        return h == null ? null : h.session;
    }

    public static synchronized void release(String id) {
        Handle h = OPEN.remove(id);
        if (h != null) h.session.close();
    }

    public static synchronized List<Map.Entry<String, Handle>> all() {
        return new ArrayList<>(OPEN.entrySet());
    }
}
