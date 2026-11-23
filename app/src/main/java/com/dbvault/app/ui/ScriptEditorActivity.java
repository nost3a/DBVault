package com.dbvault.app.ui;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dbvault.app.App;
import com.dbvault.app.R;
import com.dbvault.app.db.DatabaseSession;
import com.dbvault.app.util.Sessions;
import com.dbvault.app.util.SqlScript;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 打开 .sql 脚本，编辑、保存、并执行到一个已连接的库
 * edit and run a .sql file
 */
public class ScriptEditorActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Uri uri;
    private String name;
    private EditText editor;
    private TextView hint;
    private TextView title;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        String uriString = getIntent().getStringExtra("uri");
        name = getIntent().getStringExtra("name");
        if (uriString == null) {
            finish();
            return;
        }
        uri = Uri.parse(uriString);

        editor = findViewById(R.id.editor);
        hint = findViewById(R.id.hint);
        title = findViewById(R.id.title);

        title.setText(name == null ? getString(R.string.script_default_name) : name);
        hint.setText(R.string.script_hint);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnExec).setOnClickListener(v -> chooseTarget());

        loadText();
    }

    private void loadText() {
        io.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException(getString(R.string.err_empty_or_unreadable));
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                String text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                runOnUiThread(() -> {
                    editor.setText(text);
                    hint.setText(getString(R.string.script_loaded, text.length()));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hint.setText(getString(R.string.status_read_failed, String.valueOf(e.getMessage())));
                    Toast.makeText(this, R.string.title_read_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void save() {
        String text = editor.getText().toString();
        io.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException(getString(R.string.err_not_writable));
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                runOnUiThread(() -> Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.title_save_failed)
                        .setMessage(String.valueOf(e.getMessage()))
                        .setPositiveButton(R.string.action_got_it, null)
                        .show());
            }
        });
    }

    private void chooseTarget() {
        List<Map.Entry<String, Sessions.Handle>> list = Sessions.all();
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Sessions.Handle> e : list) {
            DatabaseSession s = e.getValue().session;
            labels.add(s.title() + "  ·  " + s.dialect());
            ids.add(e.getKey());
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.script_no_session, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.script_pick_target)
                .setItems(labels.toArray(new String[0]), (d, which) -> execute(ids.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void execute(String sessionId) {
        DatabaseSession session = Sessions.get(sessionId);
        if (session == null) return;
        String text = editor.getText().toString();
        List<String> statements = SqlScript.split(text);
        if (statements.isEmpty()) {
            Toast.makeText(this, R.string.script_no_statements, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append(getString(R.string.script_target, session.title())).append('\n');
        preview.append(getString(R.string.script_statement_count, statements.size())).append('\n');
        int writes = 0;
        for (String s : statements) if (SqlScript.isWrite(s)) writes++;
        preview.append(getString(R.string.script_write_count, writes)).append("\n\n");
        preview.append(session.isReadOnly() ? getString(R.string.script_readonly_note) + "\n" : "");
        preview.append(getString(R.string.script_confirm_ask));

        new AlertDialog.Builder(this)
                .setTitle(R.string.script_dialog_title)
                .setMessage(preview.toString())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_execute, (d, w) -> runStatements(session, statements))
                .show();
    }

    private void runStatements(DatabaseSession session, List<String> statements) {
        hint.setText(R.string.script_running);
        io.execute(() -> {
            int ok = 0;
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i);
                try {
                    if (SqlScript.isWrite(stmt) && session.isReadOnly()) {
                        throw new IllegalStateException(getString(R.string.toast_readonly));
                    }
                    session.query(stmt, 50);
                    ok++;
                } catch (Throwable e) {
                    String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    failures.add(getString(R.string.script_stmt_failed, i + 1, m));
                    if (failures.size() >= 8) break;
                }
            }
            final int okFinal = ok;
            final List<String> failFinal = failures;
            runOnUiThread(() -> {
                hint.setText(getString(R.string.script_done, okFinal, failFinal.size()));
                if (!failFinal.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.script_partial_fail)
                            .setMessage(String.join("\n\n", failFinal))
                            .setPositiveButton(R.string.action_got_it, null)
                            .show();
                } else {
                    Toast.makeText(this, R.string.script_all_done, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
