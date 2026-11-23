package com.dbvault.app.ui;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dbvault.app.R;
import com.dbvault.app.db.ConnectionProfile;
import com.dbvault.app.db.ConnectionStore;
import com.dbvault.app.db.DatabaseSession;
import com.dbvault.app.db.DatabaseSources;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 连接表单：MySQL / PostgreSQL / SQL Server / Redis 四种类型 */
public class ConnectionEditActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ConnectionStore store;
    private ConnectionProfile profile;

    private TextView typeMysql;
    private TextView typePg;
    private TextView typeMssql;
    private TextView typeRedis;
    private TextView dbLabel;
    private TextView schemaLabel;
    private TextView note;
    private TextView title;

    private EditText alias;
    private EditText host;
    private EditText port;
    private EditText user;
    private EditText password;
    private EditText database;
    private EditText schema;

    private boolean passwordVisible;
    private boolean dirty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_edit);

        store = new ConnectionStore(this);

        typeMysql = findViewById(R.id.typeMysql);
        typePg = findViewById(R.id.typePg);
        typeMssql = findViewById(R.id.typeMssql);
        typeRedis = findViewById(R.id.typeRedis);
        dbLabel = findViewById(R.id.dbLabel);
        schemaLabel = findViewById(R.id.schemaLabel);
        note = findViewById(R.id.note);
        title = findViewById(R.id.title);

        alias = findViewById(R.id.alias);
        host = findViewById(R.id.host);
        port = findViewById(R.id.port);
        user = findViewById(R.id.user);
        password = findViewById(R.id.password);
        database = findViewById(R.id.database);
        schema = findViewById(R.id.schema);

        String id = getIntent().getStringExtra("profileId");
        profile = find(id);
        if (profile == null) {
            profile = new ConnectionProfile();
            title.setText(R.string.title_new_conn);
        } else {
            title.setText(R.string.title_edit_conn);
        }

        bind(profile);

        typeMysql.setOnClickListener(v -> switchType(ConnectionProfile.TYPE_MYSQL));
        typePg.setOnClickListener(v -> switchType(ConnectionProfile.TYPE_POSTGRES));
        typeMssql.setOnClickListener(v -> switchType(ConnectionProfile.TYPE_MSSQL));
        typeRedis.setOnClickListener(v -> switchType(ConnectionProfile.TYPE_REDIS));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnTest).setOnClickListener(v -> test());
        findViewById(R.id.togglePassword).setOnClickListener(v -> togglePassword());
        findViewById(R.id.schema).setOnClickListener(v -> {
        });
    }

    private ConnectionProfile find(String id) {
        if (TextUtils.isEmpty(id)) return null;
        for (ConnectionProfile p : store.load()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private void bind(ConnectionProfile p) {
        alias.setText(p.label);
        host.setText(p.host);
        port.setText(String.valueOf(p.port > 0 ? p.port : ConnectionProfile.defaultPort(p.type)));
        user.setText(p.user);
        password.setText(p.password);
        database.setText(p.database);
        schema.setText(p.schema);
        applyType(p.type, false);
    }

    private void switchType(String type) {
        if (type.equals(profile.type) && dirty) return;
        profile.type = type;
        int current = parseInt(port.getText().toString(), 0);
        int standard = ConnectionProfile.defaultPort(type);
        // 端口还是上一个类型的默认值就跟着换，用户手填过则保留
        // follow the new type only if the port is still some default, keep whatever the user typed
        if (current == 0 || current == standard
                || current == ConnectionProfile.defaultPort(ConnectionProfile.TYPE_MYSQL)
                || current == 5432 || current == 1433 || current == 6379) {
            port.setText(String.valueOf(standard));
        }
        applyType(type, true);
        dirty = true;
    }

    private void applyType(String type, boolean resetFields) {
        int active = R.drawable.bg_btn;
        int idle = R.drawable.bg_field;
        int on = R.color.text;
        int off = R.color.text_dim;

        setTypeStyle(typeMysql, type.equals(ConnectionProfile.TYPE_MYSQL) ? active : idle,
                type.equals(ConnectionProfile.TYPE_MYSQL) ? on : off);
        setTypeStyle(typePg, type.equals(ConnectionProfile.TYPE_POSTGRES) ? active : idle,
                type.equals(ConnectionProfile.TYPE_POSTGRES) ? on : off);
        setTypeStyle(typeMssql, type.equals(ConnectionProfile.TYPE_MSSQL) ? active : idle,
                type.equals(ConnectionProfile.TYPE_MSSQL) ? on : off);
        setTypeStyle(typeRedis, type.equals(ConnectionProfile.TYPE_REDIS) ? active : idle,
                type.equals(ConnectionProfile.TYPE_REDIS) ? on : off);

        boolean redis = ConnectionProfile.TYPE_REDIS.equals(type);
        boolean pg = ConnectionProfile.TYPE_POSTGRES.equals(type);

        dbLabel.setText(redis ? R.string.label_database_redis : R.string.label_database);
        database.setHint(redis ? R.string.hint_db_redis : pg ? R.string.hint_db_pg : R.string.hint_db_default);

        schemaLabel.setVisibility(pg || ConnectionProfile.TYPE_MSSQL.equals(type)
                ? View.VISIBLE : View.GONE);
        schema.setVisibility(pg || ConnectionProfile.TYPE_MSSQL.equals(type)
                ? View.VISIBLE : View.GONE);
        schema.setHint(pg ? R.string.hint_schema_pg : R.string.hint_schema_mssql);

        user.setEnabled(!redis);
        user.setAlpha(redis ? 0.4f : 1f);

        if (redis) {
            note.setText(R.string.note_redis);
        } else if (pg) {
            note.setText(R.string.note_pg);
        } else if (ConnectionProfile.TYPE_MSSQL.equals(type)) {
            note.setText(R.string.note_mssql);
        } else {
            note.setText(R.string.note_mysql);
        }

        if (resetFields) {
            if (TextUtils.isEmpty(alias.getText())) {
                alias.setHint(redis ? R.string.hint_alias_redis : pg ? R.string.hint_alias_pg : R.string.hint_alias_mysql);
            }
        }
    }

    private void setTypeStyle(TextView v, int bg, int colorRes) {
        v.setBackgroundResource(bg);
        v.setTextColor(getResources().getColor(colorRes, getTheme()));
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        password.setInputType(passwordVisible
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSelection(password.getText().length());
        ((TextView) findViewById(R.id.togglePassword)).setText(passwordVisible ? R.string.action_hide_pwd : R.string.action_show_pwd);
    }

    private ConnectionProfile collect() {
        ConnectionProfile p = profile.copy();
        p.label = alias.getText().toString().trim();
        p.host = host.getText().toString().trim();
        p.port = parseInt(port.getText().toString(), ConnectionProfile.defaultPort(p.type));
        p.user = user.getText().toString().trim();
        p.password = password.getText().toString();
        p.database = database.getText().toString().trim();
        p.schema = schema.getText().toString().trim();
        p.ssl = ((com.google.android.material.switchmaterial.SwitchMaterial)
                findViewById(R.id.ssl)).isChecked();
        return p;
    }

    private boolean validate(ConnectionProfile p) {
        if (p.host.isEmpty()) {
            toast(getString(R.string.toast_need_host));
            return false;
        }
        if (!ConnectionProfile.TYPE_REDIS.equals(p.type)
                && (p.database.isEmpty() && !ConnectionProfile.TYPE_MYSQL.equals(p.type))) {
            toast(getString(R.string.toast_need_db));
            return false;
        }
        if (!ConnectionProfile.TYPE_REDIS.equals(p.type) && p.user.isEmpty()) {
            toast(getString(R.string.toast_need_user));
            return false;
        }
        return true;
    }

    private void test() {
        ConnectionProfile p = collect();
        if (!validate(p)) return;

        toast(getString(R.string.toast_connecting));
        io.execute(() -> {
            DatabaseSession s = null;
            try {
                s = DatabaseSources.remoteOpen(p);
                int count = s.objects().size();
                String info = s.dialect() + " · " + count + " objects";
                if (s instanceof com.dbvault.app.db.JdbcSession) {
                    String v = ((com.dbvault.app.db.JdbcSession) s).serverInfo();
                    if (!TextUtils.isEmpty(v)) info = v + "\n" + info;
                }
                final String msg = info;
                runOnUiThread(() -> toast(getString(R.string.toast_connected, msg)));
            } catch (Throwable e) {
                runOnUiThread(() -> toast(DatabaseSources.describe(this, e)));
            } finally {
                if (s != null) s.close();
            }
        });
    }

    private void save() {
        ConnectionProfile p = collect();
        if (!validate(p)) return;
        store.upsert(p);
        toast(getString(R.string.toast_saved));
        finish();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
