package com.aiwriting.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aiwriting.app.R;
import com.aiwriting.app.db.StyleMemoryEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<StyleMemoryEntry> items = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public void setItems(List<StyleMemoryEntry> entries) {
        this.items = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StyleMemoryEntry entry = items.get(position);
        holder.tvOriginal.setText(entry.originalText);
        holder.tvRewritten.setText(entry.rewrittenText);
        holder.tvMeta.setText(entry.tone + " · " + entry.appContext
                + " · " + sdf.format(new Date(entry.timestamp)));

        // Long-press rewritten text to copy
        holder.tvRewritten.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("rewrite", entry.rewrittenText));
            Toast.makeText(v.getContext(), "Copied!", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOriginal, tvRewritten, tvMeta;

        ViewHolder(View itemView) {
            super(itemView);
            tvOriginal  = itemView.findViewById(R.id.tv_original);
            tvRewritten = itemView.findViewById(R.id.tv_rewritten);
            tvMeta      = itemView.findViewById(R.id.tv_meta);
        }
    }
}