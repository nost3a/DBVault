package com.dbvault.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dbvault.app.R;
import com.dbvault.app.adapter.ObjectAdapter;
import com.dbvault.app.db.ColumnMeta;
import com.dbvault.app.db.DatabaseSession;
import com.dbvault.app.db.DbObject;
import com.dbvault.app.db.QueryResult;
import com.dbvault.app.db.Sql;
import com.dbvault.app.db.SqliteSession;
import com.dbvault.app.util.LocalFiles;
import com.dbvault.app.util.Sessions;
import com.dbvault.app.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主体工作区：左侧对象树，右侧数据网格。编辑器式布局，表头与数据横向联动。
 * Main workspace: object tree on the left, data grid on the right.
 */
public class ExplorerActivity extends AppCompatActivity {

    private static final String TAG = "Explorer";
    private static final int PAGE = 50;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private String sessionId;
    private DatabaseSession session;

    private RecyclerView objectsView;
    private ObjectAdapter objectAdapter;

    private LinearLayout sidePane;
    private View splitter;
    private SharedPreferences uiPrefs;
    private static final String PREF_UI = "dbvault.ui";
    private static final String KEY_SIDE_WIDTH = "explorer_side_width";
    private static final int SIDE_MIN_DP = 96;
    private static final int SIDE_DEFAULT_DP = 146;
    private static final int SIDE_PANEL_MIN_DP = 220;

    private LinearLayout tablePane;
    private TextView placeholder;
    private HorizontalScrollView headerScroll;
    private HorizontalScrollView dataHScroll;
    private LinearLayout headerRow;
    private LinearLayout grid;
    private LinearLayout pager;

    private TextView title;
    private TextView status;
    private TextView pageInfo;
    private TextView btnPrev;
    private TextView btnNext;

    private final List<DbObject> objects = new ArrayList<>();
    private List<ColumnMeta> columns = new ArrayList<>();
    private QueryResult current = QueryResult.empty();
    private String table;
    private int offset = 0;

    private int[] widths;
    private boolean lockScroll;
    private boolean created;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explorer);

        sessionId = getIntent().getStringExtra("session");
        session = Sessions.get(sessionId);
        if (session == null) {
            finish();
            return;
        }

        sidePane = findViewById(R.id.sidePane);
        splitter = findViewById(R.id.splitter);
        uiPrefs = getSharedPreferences(PREF_UI, MODE_PRIVATE);

        title = findViewById(R.id.title);
        status = findViewById(R.id.status);
        placeholder = findViewById(R.id.placeholder);
        tablePane = findViewById(R.id.tablePane);
        headerScroll = findViewById(R.id.headerScroll);
        dataHScroll = findViewById(R.id.dataHScroll);
        headerRow = findViewById(R.id.headerRow);
        grid = findViewById(R.id.grid);
        pager = findViewById(R.id.pager);
        pageInfo = findViewById(R.id.pageInfo);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        title.setText(session.title());

        objectsView = findViewById(R.id.objects);
        objectsView.setLayoutManager(new LinearLayoutManager(this));
        objectAdapter = new ObjectAdapter(this::openTable);
        objectsView.setAdapter(objectAdapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            if (table != null) {
                offset = 0;
                loadTable(table);
            } else {
                loadObjects();
            }
        });
        findViewById(R.id.btnSql).setOnClickListener(v -> {
            Intent i = new Intent(this, SqlConsoleActivity.class);
            i.putExtra("session", sessionId);
            i.putExtra("table", table);
            startActivity(i);
        });

        btnPrev.setOnClickListener(v -> turnPage(-1));
        btnNext.setOnClickListener(v -> turnPage(1));

        bindScroll(headerScroll, dataHScroll);
        bindScroll(dataHScroll, headerScroll);
        setupSplitter();

        loadObjects();
        created = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从 SQL 控制台改完数据回来，刷新当前表
        // back from the console: reload the table, it may have changed
        // refresh after the console writes
        if (created && table != null) loadTable(table);
    }

    /**
     * 侧栏宽度拖动：按下高亮，移动实时改宽，松手回写。
     * 单击（没发生位移）复位到默认宽度。
     * Drag the sidebar edge to resize: highlight on press, live width on move, persist on release.
     * A tap that never moved resets the width to the default.
     */
    private void setupSplitter() {
        int minPx = Ui.dp(this, SIDE_MIN_DP);
        int maxPx = Math.max(minPx + 40,
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, SIDE_PANEL_MIN_DP));

        int saved = uiPrefs.getInt(KEY_SIDE_WIDTH, Ui.dp(this, SIDE_DEFAULT_DP));
        applySideWidth(Math.min(Math.max(saved, minPx), maxPx));

        final int[] startX = new int[1];
        final int[] startW = new int[1];
        final boolean[] moved = new boolean[1];

        splitter.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = (int) event.getRawX();
                    startW[0] = sidePane.getWidth();
                    moved[0] = false;
                    v.setBackgroundColor(Ui.color(this, R.color.accent_dim));
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = (int) event.getRawX() - startX[0];
                    if (Math.abs(dx) > Ui.dp(this, 3)) moved[0] = true;
                    int w = Math.min(Math.max(startW[0] + dx, minPx), maxPx);
                    applySideWidth(w);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setBackgroundColor(Ui.color(this, R.color.border));
                    if (!moved[0]) {
                        applySideWidth(Ui.dp(this, SIDE_DEFAULT_DP));
                        uiPrefs.edit().putInt(KEY_SIDE_WIDTH, Ui.dp(this, SIDE_DEFAULT_DP)).apply();
                    } else {
                        uiPrefs.edit().putInt(KEY_SIDE_WIDTH, sidePane.getWidth()).apply();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void applySideWidth(int px) {
        ViewGroup.LayoutParams lp = sidePane.getLayoutParams();
        if (lp.width == px) return;
        lp.width = px;
        sidePane.setLayoutParams(lp);
    }

    private void bindScroll(HorizontalScrollView from, HorizontalScrollView to) {
        from.setOnScrollChangeListener((View v, int x, int y, int ox, int oy) -> {
            if (lockScroll) return;
            lockScroll = true;
            to.scrollTo(x, 0);
            lockScroll = false;
        });
    }

    private void loadObjects() {
        status.setText("loading objects…");
        io.execute(() -> {
            try {
                List<DbObject> list = session.objects();
                runOnUiThread(() -> {
                    objects.clear();
                    objects.addAll(list);
                    objectAdapter.submit(objects, table);
                    int views = 0;
                    for (DbObject o : objects) if (o.isView()) views++;
                    status.setText(session.dialect() + " · " + (objects.size() - views) + " tables"
                            + (views > 0 ? " · " + views + " views" : ""));
                    if (table == null && !list.isEmpty()) {
                        openTable(list.get(0));
                    }
                });
            } catch (Throwable e) {
                runOnUiThread(() -> status.setText("objects failed: " + msg(e)));
            }
        });
    }

    private void openTable(DbObject o) {
        table = o.name;
        offset = 0;
        objectAdapter.submit(objects, table);
        tablePane.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        loadTable(table);
    }

    private void loadTable(String name) {
        status.setText("reading " + name + " …");
        io.execute(() -> {
            try {
                List<ColumnMeta> cols = session.columns(name);
                QueryResult res = session.browse(name, offset, PAGE);
                runOnUiThread(() -> {
                    columns = cols;
                    current = res;
                    render();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    grid.removeAllViews();
                    headerRow.removeAllViews();
                    status.setText("read failed: " + msg(e));
                });
            }
        });
    }

    private void render() {
        headerRow.removeAllViews();
        grid.removeAllViews();

        if (columns.isEmpty()) {
            status.setText(session.dialect() + " · " + table + " · no readable columns");
            return;
        }

        widths = measureWidths();

        // 表头
        // header row
        TextView rowNumHead = headerCell("#", Ui.dp(this, 40));
        rowNumHead.setTextColor(Ui.color(this, R.color.text_dim));
        headerRow.addView(rowNumHead);

        for (int i = 0; i < columns.size(); i++) {
            ColumnMeta c = columns.get(i);
            String label = c.name + (c.primaryKey ? " *" : "");
            TextView h = headerCell(label, widths[i]);
            h.setTextColor(Ui.color(this, c.primaryKey ? R.color.accent : R.color.text));
            headerRow.addView(h);
        }

        if (current.rowCount() == 0) {
            TextView hint = new TextView(this);
            hint.setText("0 rows");
            hint.setTextSize(11f);
            hint.setTypeface(Typeface.MONOSPACE);
            hint.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            hint.setTextColor(Ui.color(this, R.color.text_dim));
            grid.addView(hint);
            updatePager();
            updateStatus();
            return;
        }

        for (int r = 0; r < current.rowCount(); r++) {
            final int row = r;
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);

            TextView idx = new TextView(this);
            idx.setText(String.valueOf(offset + r + 1));
            idx.setTextSize(10f);
            idx.setTypeface(Typeface.MONOSPACE);
            idx.setGravity(Gravity.CENTER_VERTICAL);
            idx.setPadding(Ui.dp(this, 10), 0, 0, 0);
            idx.setTextColor(Ui.color(this, R.color.text_dim));
            idx.setBackgroundColor(Ui.color(this, r % 2 == 0 ? R.color.bg : R.color.row_alt));
            line.addView(idx, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 30)));

            for (int c = 0; c < current.columnCount(); c++) {
                final int col = c;
                String value = current.rows[r][c];

                TextView cell = new TextView(this);
                cell.setTextSize(11f);
                cell.setTypeface(Typeface.MONOSPACE);
                cell.setSingleLine(true);
                cell.setEllipsize(TextUtils.TruncateAt.END);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
                cell.setBackgroundColor(Ui.color(this, r % 2 == 0 ? R.color.bg : R.color.row_alt));

                if (value == null) {
                    cell.setText("NULL");
                    cell.setTextColor(Ui.color(this, R.color.text_dim));
                    cell.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
                } else if (value.isEmpty()) {
                    cell.setText("");
                } else {
                    cell.setText(value.replace('\n', ' '));
                    cell.setTextColor(Ui.color(this, R.color.text));
                }

                cell.setOnClickListener(v -> editCell(row, col));
                line.addView(cell, new LinearLayout.LayoutParams(widths[c], Ui.dp(this, 30)));
            }

            grid.addView(line);

            View sep = new View(this);
            sep.setBackgroundColor(Ui.color(this, R.color.border));
            grid.addView(sep, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }

        updatePager();
        updateStatus();
    }

    private TextView headerCell(String text, int width) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        t.setTextColor(Ui.color(this, R.color.text));
        t.setLayoutParams(new LinearLayout.LayoutParams(width, Ui.dp(this, 28)));
        return t;
    }

    private int[] measureWidths() {
        int[] w = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            int best = Math.max(columns.get(c).name.length() + 4, 5);
            for (int r = 0; r < current.rowCount(); r++) {
                String v = current.rows[r][c];
                if (v != null) best = Math.max(best, Math.min(v.length(), 30));
            }
            int dp = Math.max(72, Math.min(220, best * 6 + 20));
            w[c] = Ui.dp(this, dp);
        }
        return w;
    }

    private void updatePager() {
        int from = current.rowCount() == 0 ? offset : offset + 1;
        int to = offset + current.rowCount();
        pageInfo.setText(from + "–" + to);
        btnPrev.setAlpha(offset == 0 ? 0.35f : 1f);
        btnNext.setAlpha(current.rowCount() < PAGE ? 0.35f : 1f);
    }

    private void updateStatus() {
        String tail = current.rowCount() == 0 ? "" : " · " + current.elapsedMs + "ms";
        status.setText(session.dialect() + " · " + table + " · " + current.rowCount() + " rows" + tail);
    }

    private void turnPage(int dir) {
        if (table == null) return;
        int next = offset + dir * PAGE;
        if (next < 0) {
            toast(getString(R.string.toast_first_page));
            return;
        }
        offset = next;
        loadTable(table);
    }

    private void editCell(int row, int col) {
        if (session.isReadOnly()) {
            toast(getString(R.string.toast_readonly));
            return;
        }
        if ("redis".equals(session.dialect())) {
            toast(getString(R.string.toast_redis_edit));
            return;
        }
        if (!session.supportsSql() && !session.supportsCellUpdate()) {
            toast(getString(R.string.toast_format_readonly));
            return;
        }
        if (col >= columns.size() || col >= current.columnCount()) {
            toast(getString(R.string.toast_columns_missing));
            return;
        }
        ColumnMeta c = columns.get(col);
        String old = current.rows[row][col];

        EditText input = new EditText(this);
        input.setText(old == null ? "" : old);
        input.setTextColor(Ui.color(this, R.color.text));
        input.setTextSize(13f);
        input.setTypeface(Typeface.MONOSPACE);
        input.setBackgroundResource(R.drawable.bg_field);
        input.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(c.name + "  " + c.shortType())
                .setView(wrap)
                .setNeutralButton(R.string.action_null, (d, w) -> applyEdit(row, col, null, true))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_write, (d, w) -> applyEdit(row, col, input.getText().toString(), false))
                .show();
    }

    private void applyEdit(int row, int col, String value, boolean isNull) {
        ColumnMeta c = columns.get(col);
        String set = session.quote(c.name) + " = " + (isNull ? "NULL" : Sql.literal(value));

        // 非 SQL 引擎（Access / dbf / csv）：按绝对行号直接写单元格
        // non-SQL engines (Access / dbf / csv) are written by absolute row index
        if (!session.supportsSql() && session.supportsCellUpdate()) {
            io.execute(() -> {
                try {
                    session.updateCellAt(table, offset + row, c.name, value, isNull);
                    runOnUiThread(() -> loadTable(table));
                } catch (Throwable e) {
                    runOnUiThread(() -> showError(e));
                }
            });
            return;
        }

        List<ColumnMeta> keys = new ArrayList<>();
        for (ColumnMeta m : columns) if (m.primaryKey) keys.add(m);
        boolean byPrimaryKey = !keys.isEmpty();
        List<ColumnMeta> keyCols = byPrimaryKey ? keys : columns;

        StringBuilder where = new StringBuilder();
        for (ColumnMeta k : keyCols) {
            int idx = columns.indexOf(k);
            if (idx < 0 || idx >= current.columnCount()) continue;
            String v = current.rows[row][idx];
            if (where.length() > 0) where.append(" AND ");
            if (v == null) {
                where.append(session.quote(k.name)).append(" IS NULL");
            } else {
                where.append(session.quote(k.name)).append(" = ").append(Sql.literal(v));
            }
        }
        if (where.length() == 0) {
            toast(getString(R.string.toast_no_condition));
            return;
        }

        String sql = "UPDATE " + session.quote(table) + " SET " + set + " WHERE " + where;

        Runnable commit = () -> io.execute(() -> {
            try {
                int affected = session.update(sql);
                syncMirror();
                runOnUiThread(() -> {
                    if (affected >= 0) toast(getString(R.string.status_affected, affected));
                    loadTable(table);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> showError(e));
            }
        });

        if (byPrimaryKey) {
            commit.run();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_pk_title)
                    .setMessage(R.string.no_pk_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_continue, (d, w) -> commit.run())
                    .show();
        }
    }

    private void syncMirror() {
        Sessions.Handle h = Sessions.handle(sessionId);
        if (h == null || h.mirroredFile == null || h.sourceUri == null) return;
        try {
            if (session instanceof SqliteSession) ((SqliteSession) session).flush();
            LocalFiles.syncBack(h.mirroredFile, h.sourceUri);
        } catch (Exception e) {
            Log.w(TAG, "sync back failed", e);
        }
    }

    private void showError(Throwable e) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_failed)
                .setMessage(msg(e))
                .setPositiveButton("ok", null)
                .show();
    }

    private static String msg(Throwable e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
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
