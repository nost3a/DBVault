package com.dbvault.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dbvault.app.R;
import com.dbvault.app.adapter.EntryAdapter;
import com.dbvault.app.db.ConnectionProfile;
import com.dbvault.app.db.ConnectionStore;
import com.dbvault.app.db.DatabaseSources;
import com.dbvault.app.db.DatabaseSession;
import com.dbvault.app.util.LocalFiles;
import com.dbvault.app.util.LocalRecentStore;
import com.dbvault.app.util.Sessions;
import com.dbvault.app.util.Ui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 入口页：本地文件与远程连接的列表
 * start screen
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_OPEN = 2201;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private RecyclerView recentList;
    private RecyclerView connList;
    private TextView empty;
    private TextView status;
    private TextView labelLocal;
    private TextView labelRemote;
    private View indLocal;
    private View indRemote;

    private EntryAdapter fileAdapter;
    private EntryAdapter connAdapter;

    private ConnectionStore connStore;
    private LocalRecentStore recentStore;

    private int tab = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connStore = new ConnectionStore(this);
        recentStore = new LocalRecentStore(this);

        recentList = findViewById(R.id.recentList);
        connList = findViewById(R.id.connList);
        empty = findViewById(R.id.empty);
        status = findViewById(R.id.status);
        labelLocal = findViewById(R.id.labelLocal);
        labelRemote = findViewById(R.id.labelRemote);
        indLocal = findViewById(R.id.indLocal);
        indRemote = findViewById(R.id.indRemote);

        recentList.setLayoutManager(new LinearLayoutManager(this));
        connList.setLayoutManager(new LinearLayoutManager(this));

        fileAdapter = new EntryAdapter(new EntryAdapter.Listener() {
            @Override
            public void onClick(EntryAdapter.Row row) {
                openLocal((LocalRecentStore.Entry) row.tag);
            }

            @Override
            public void onLongClick(EntryAdapter.Row row) {
                LocalRecentStore.Entry e = (LocalRecentStore.Entry) row.tag;
                new AlertDialog.Builder(MainActivity.this)
                        .setItems(new String[]{getString(R.string.menu_remove)}, (d, w) -> {
                            recentStore.forget(e.uri);
                            refresh();
                        })
                        .show();
            }
        });
        recentList.setAdapter(fileAdapter);

        connAdapter = new EntryAdapter(new EntryAdapter.Listener() {
            @Override
            public void onClick(EntryAdapter.Row row) {
                connect((ConnectionProfile) row.tag);
            }

            @Override
            public void onLongClick(EntryAdapter.Row row) {
                ConnectionProfile p = (ConnectionProfile) row.tag;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(p.displayName())
                        .setItems(new String[]{getString(R.string.menu_edit), getString(R.string.menu_delete)}, (d, w) -> {
                            if (w == 0) {
                                Intent i = new Intent(MainActivity.this, ConnectionEditActivity.class);
                                i.putExtra("profileId", p.id);
                                startActivity(i);
                            } else {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setMessage(R.string.confirm_delete_conn)
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .setPositiveButton(R.string.menu_delete, (dd, ww) -> {
                                            connStore.remove(p.id);
                                            refresh();
                                        })
                                        .show();
                            }
                        })
                        .show();
            }
        });
        connList.setAdapter(connAdapter);

        findViewById(R.id.btnOpenFile).setOnClickListener(v -> pickFile());
        findViewById(R.id.btnAddConn).setOnClickListener(v ->
                startActivity(new Intent(this, ConnectionEditActivity.class)));
        findViewById(R.id.tabLocal).setOnClickListener(v -> setTab(0));
        findViewById(R.id.tabRemote).setOnClickListener(v -> setTab(1));

        setTab(0);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void setTab(int index) {
        tab = index;
        int on = Ui.color(this, R.color.text);
        int off = Ui.color(this, R.color.text_dim);

        labelLocal.setTextColor(index == 0 ? on : off);
        labelRemote.setTextColor(index == 1 ? on : off);
        indLocal.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
        indRemote.setVisibility(index == 1 ? View.VISIBLE : View.INVISIBLE);
        recentList.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        connList.setVisibility(index == 1 ? View.VISIBLE : View.GONE);

        boolean showEmpty = index == 0 ? fileAdapter.getItemCount() == 0 : connAdapter.getItemCount() == 0;
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        empty.setText(index == 0 ? R.string.empty_local : R.string.empty_remote);
    }

    private void refresh() {
        List<LocalRecentStore.Entry> files = recentStore.load();
        List<EntryAdapter.Row> fileRows = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
        for (LocalRecentStore.Entry e : files) {
            String kind = "script".equals(e.kind) ? "SQL" : "SQLITE";
            fileRows.add(new EntryAdapter.Row(kind, e.name,
                    Ui.humanSize(e.size) + "  " + fmt.format(new Date(e.lastUsed)), e));
        }
        fileAdapter.submit(fileRows);

        List<ConnectionProfile> conns = connStore.load();
        List<EntryAdapter.Row> connRows = new ArrayList<>();
        for (ConnectionProfile p : conns) {
            String kind = "postgresql".equals(p.type) ? "PG"
                    : "mssql".equals(p.type) ? "MSSQL"
                    : "redis".equals(p.type) ? "REDIS" : "MYSQL";
            String db = p.database == null || p.database.isEmpty() ? "" : "  " + p.database;
            connRows.add(new EntryAdapter.Row(kind, p.displayName(),
                    p.host + ":" + p.port + db, p));
        }
        connAdapter.submit(connRows);

        boolean showEmpty = tab == 0 ? fileRows.isEmpty() : connRows.isEmpty();
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        status.setText(tab == 0 ? getString(R.string.status_files, files.size())
                : getString(R.string.status_conns, conns.size()));
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            toast(getString(R.string.no_file_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            LocalFiles.keepPermission(this, uri);
            openUri(uri);
        }
    }

    private void openLocal(LocalRecentStore.Entry entry) {
        Uri uri = Uri.parse(entry.uri);
        if ("script".equals(entry.kind)) {
            Intent i = new Intent(this, ScriptEditorActivity.class);
            i.putExtra("uri", entry.uri);
            i.putExtra("name", entry.name);
            startActivity(i);
            return;
        }
        if (!LocalFiles.hasWrite(this, uri)) {
            toast(getString(R.string.toast_permission_lost));
            pickFile();
            return;
        }
        openUri(uri);
    }

    private void openUri(Uri uri) {
        final AlertDialog dlg = busy(getString(R.string.busy_reading));
        io.execute(() -> {
            try {
                String name = LocalFiles.displayName(MainActivity.this, uri);
                if ("script".equals(LocalFiles.guessKind(name))) {
                    runOnUiThread(() -> {
                        dlg.dismiss();
                        Intent i = new Intent(MainActivity.this, ScriptEditorActivity.class);
                        i.putExtra("uri", uri.toString());
                        i.putExtra("name", name);
                        startActivity(i);
                    });
                    return;
                }

                File mirrored = LocalFiles.mirror(MainActivity.this, uri, name);
                DatabaseSession session = DatabaseSources.localOpen(mirrored);
                String id = Sessions.attach(session, uri.toString(), mirrored);
                recentStore.touch(uri.toString(), name, mirrored.length(), "sqlite");

                runOnUiThread(() -> {
                    dlg.dismiss();
                    refresh();
                    launch(id);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    dlg.dismiss();
                    showError(getString(R.string.title_open_failed), e);
                });
            }
        });
    }

    private void connect(ConnectionProfile profile) {
        final AlertDialog dlg = busy(getString(R.string.busy_connecting, profile.host));
        io.execute(() -> {
            try {
                DatabaseSession session = DatabaseSources.remoteOpen(profile);
                profile.lastUsed = System.currentTimeMillis();
                connStore.upsert(profile);
                String id = Sessions.attach(session);
                runOnUiThread(() -> {
                    dlg.dismiss();
                    refresh();
                    launch(id);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    dlg.dismiss();
                    showError(getString(R.string.title_connect_failed), e);
                });
            }
        });
    }

    private void launch(String sessionId) {
        Intent i = new Intent(this, ExplorerActivity.class);
        i.putExtra("session", sessionId);
        startActivity(i);
    }

    private AlertDialog busy(String message) {
        AlertDialog d = new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .create();
        d.show();
        return d;
    }

    private void showError(String title, Throwable e) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(DatabaseSources.describe(this, e))
                .setPositiveButton(R.string.action_close, null)
                .show();
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
