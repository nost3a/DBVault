package com.dbvault.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 把脚本按语句切开，跳过字符串与注释里的分号
 * split a script into statements
 */
public final class SqlScript {

    private SqlScript() {
    }

    public static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;

        StringBuilder cur = new StringBuilder();
        int i = 0;
        int len = script.length();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        while (i < len) {
            char c = script.charAt(i);

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    cur.append(c);
                }
                i++;
                continue;
            }
            if (blockComment) {
                if (c == '*' && i + 1 < len && script.charAt(i + 1) == '/') {
                    blockComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (quote != 0) {
                cur.append(c);
                if (c == quote) {
                    if (i + 1 < len && script.charAt(i + 1) == quote) {
                        cur.append(script.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    quote = 0;
                } else if (c == '\\' && i + 1 < len) {
                    cur.append(script.charAt(i + 1));
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }

            if (c == '-' && i + 1 < len && script.charAt(i + 1) == '-') {
                lineComment = true;
                i += 2;
                continue;
            }
            if (c == '/' && i + 1 < len && script.charAt(i + 1) == '*') {
                blockComment = true;
                i += 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                cur.append(c);
                i++;
                continue;
            }
            if (c == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
                i++;
                continue;
            }
            cur.append(c);
            i++;
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    /**
     * 粗略判断语句是不是写操作
     * rough write detection
     */
    public static boolean isWrite(String stmt) {
        String s = stmt.trim().toUpperCase(java.util.Locale.ROOT);
        return s.startsWith("INSERT") || s.startsWith("UPDATE") || s.startsWith("DELETE")
                || s.startsWith("DROP") || s.startsWith("ALTER") || s.startsWith("CREATE")
                || s.startsWith("TRUNCATE") || s.startsWith("REPLACE")
                || s.startsWith("GRANT") || s.startsWith("REVOKE");
    }
}
