package com.dbvault.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dbvault.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件与连接共用的一行条目
 * one row type for both files and saved connections
 */
public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.VH> {

    public static class Row {
        public final String kind;
        public final String name;
        public final String meta;
        public final Object tag;

        public Row(String kind, String name, String meta, Object tag) {
            this.kind = kind;
            this.name = name;
            this.meta = meta;
            this.tag = tag;
        }
    }

    public interface Listener {
        void onClick(Row row);

        void onLongClick(Row row);
    }

    private final List<Row> items = new ArrayList<>();
    private final Listener listener;

    public EntryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Row> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_entry, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = items.get(position);
        h.kind.setText(r.kind);
        h.name.setText(r.name);
        h.meta.setText(r.meta);
        h.itemView.setOnClickListener(v -> listener.onClick(r));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(r);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView kind;
        final TextView name;
        final TextView meta;

        VH(@NonNull View v) {
            super(v);
            kind = v.findViewById(R.id.kind);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
        }
    }
}
