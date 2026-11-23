package com.dbvault.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dbvault.app.R;
import com.dbvault.app.db.DatabaseSession;
import com.dbvault.app.db.QueryResult;
import com.dbvault.app.util.Sessions;
import com.dbvault.app.util.SqlScript;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 控制台：SQL 会话里跑 SQL，Redis 会话里跑命令。输出按终端风格追加。
 * Console: SQL for SQL engines, raw commands for Redis. Output appends like a terminal.
 */
public class SqlConsoleActivity extends AppCompatActivity {

    private static final int MAX_OUTPUT_LINES = 400;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final StringBuilder history = new StringBuilder();

    private DatabaseSession session;
    private EditText editor;
    private TextView result;
    private TextView stat;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sql_console);

        session = Sessions.get(getIntent().getStringExtra("session"));
        if (session == null) {
            finish();
            return;
        }

        editor = findViewById(R.id.editor);
        result = findViewById(R.id.result);
        stat = findViewById(R.id.stat);
        TextView title = findViewById(R.id.title);
        TextView target = findViewById(R.id.target);

        title.setText(session.title());
        target.setText(session.dialect() + " · " + session.subtitle());

        String table = getIntent().getStringExtra("table");
        if ("redis".equals(session.dialect())) {
            editor.setHint(R.string.hint_redis);
            editor.setText("KEYS *");
            target.setText(getString(R.string.target_redis, session.subtitle()));
        } else if (!session.supportsSql()) {
            editor.setEnabled(false);
            editor.setHint("");
            target.setText(getString(R.string.target_unsupported, session.subtitle()));
            append(getString(R.string.console_unsupported_line, session.dialect()));
        } else {
            String t = TextUtils.isEmpty(table) ? "your_table" : session.quote(table);
            editor.setText(getString(R.string.sql_preview, t));
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRun).setOnClickListener(v -> run());
        findViewById(R.id.btnClearEditor).setOnClickListener(v -> editor.setText(""));
        findViewById(R.id.btnClearResult).setOnClickListener(v -> {
            history.setLength(0);
            result.setText("");
            stat.setText("");
        });
    }

    private void run() {
        if (!session.supportsSql()) {
            toast(getString(R.string.toast_unsupported));
            return;
        }
        String all = editor.getText().toString();
        if (all.trim().isEmpty()) return;

        List<String> stmts = "redis".equals(session.dialect())
                ? java.util.Collections.singletonList(all.trim())
                : SqlScript.split(all);
        if (stmts.isEmpty()) return;

        stat.setText(R.string.stat_running);
        io.execute(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stmts.size(); i++) {
                String stmt = stmts.get(i);
                if (stmts.size() > 1) {
                    sb.append(i == 0 ? "" : "\n").append("── ").append(i + 1).append(" ──\n");
                }
                sb.append('>').append(' ').append(oneLine(stmt)).append('\n');
                try {
                    QueryResult r = session.query(stmt, 200);
                    sb.append(format(r)).append('\n');
                } catch (Throwable e) {
                    sb.append("error: ").append(msg(e)).append('\n');
                }
            }
            String block = sb.toString();
            runOnUiThread(() -> {
                append(block);
                stat.setText(getString(R.string.stat_statements, stmts.size()));
            });
        });
    }

    private void append(String block) {
        history.append(block).append('\n');
        trimHistory();
        result.setText(history.toString());
    }

    private void trimHistory() {
        int lines = 0;
        for (int i = 0; i < history.length(); i++) if (history.charAt(i) == '\n') lines++;
        while (lines > MAX_OUTPUT_LINES) {
            int cut = history.indexOf("\n");
            if (cut < 0) break;
            history.delete(0, cut + 1);
            lines--;
        }
    }

    private static String oneLine(String s) {
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    private static String format(QueryResult r) {
        if (r.rowCount() == 0) {
            return "(0 rows)";
        }
        int n = r.columnCount();
        int[] w = new int[n];
        for (int i = 0; i < n; i++) w[i] = Math.max(1, r.columns[i] == null ? 1 : r.columns[i].length());
        for (String[] row : r.rows) {
            for (int i = 0; i < n; i++) {
                String v = row[i] == null ? "NULL" : row[i];
                w[i] = Math.max(w[i], Math.min(28, v.length()));
            }
        }

        StringBuilder sb = new StringBuilder();
        appendRow(sb, r.columns, w);
        sb.append('\n');
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < w[i]; k++) sb.append('-');
            sb.append(i == n - 1 ? "" : "  ");
        }
        sb.append('\n');
        for (String[] row : r.rows) appendRow(sb, row, w);
        sb.append("\n(").append(r.rowCount()).append(" rows");
        if (r.truncated) sb.append(", truncated");
        sb.append(", ").append(r.elapsedMs).append("ms)");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String[] row, int[] w) {
        for (int i = 0; i < w.length; i++) {
            String v = i < row.length && row[i] != null ? row[i] : "NULL";
            if (v.length() > w[i]) v = v.substring(0, Math.max(0, w[i] - 1)) + "…";
            sb.append(v);
            for (int k = v.length(); k < w[i]; k++) sb.append(' ');
            if (sb.length() > 0 && i < w.length - 1) sb.append("  ");
        }
        sb.append('\n');
    }

    private static String msg(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String m = root.getMessage();
        return m == null || m.trim().isEmpty() ? root.getClass().getSimpleName() : m;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
