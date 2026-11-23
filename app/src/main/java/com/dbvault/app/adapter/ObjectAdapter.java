package com.dbvault.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dbvault.app.R;
import com.dbvault.app.db.DbObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 侧栏对象列表
 * sidebar object list
 */
public class ObjectAdapter extends RecyclerView.Adapter<ObjectAdapter.VH> {

    public interface OnPick {
        void onPick(DbObject object);
    }

    private final List<DbObject> items = new ArrayList<>();
    private final OnPick onPick;
    private String selected;

    public ObjectAdapter(OnPick onPick) {
        this.onPick = onPick;
    }

    public void submit(List<DbObject> data, String selectedName) {
        items.clear();
        items.addAll(data);
        this.selected = selectedName;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_object, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DbObject o = items.get(position);
        h.kind.setText(o.isView() ? "V" : "T");
        h.name.setText(o.name);
        h.count.setText(o.rowCount >= 0 ? String.format(Locale.ROOT, "%,d", o.rowCount) : "");

        boolean active = selected != null && selected.equalsIgnoreCase(o.name);
        h.itemView.setBackgroundColor(active ? 0xFF094771 : 0x00000000);
        h.name.setTextColor(active ? 0xFFFFFFFF : 0xFFCCCCCC);
        h.itemView.setOnClickListener(v -> onPick.onPick(o));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView kind;
        final TextView name;
        final TextView count;

        VH(@NonNull View v) {
            super(v);
            kind = v.findViewById(R.id.kind);
            name = v.findViewById(R.id.name);
            count = v.findViewById(R.id.count);
        }
    }
}
