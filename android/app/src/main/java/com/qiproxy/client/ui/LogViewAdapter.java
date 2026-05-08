package com.qiproxy.client.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qiproxy.client.R;

import java.util.ArrayList;
import java.util.List;

/**
 * LogViewAdapter - RecyclerView adapter for displaying log lines.
 * Limits buffer to prevent memory bloat during long-running sessions.
 */
public class LogViewAdapter extends RecyclerView.Adapter<LogViewAdapter.ViewHolder> {

    private static final int MAX_LOGS = 200;

    private final List<String> logs;

    public LogViewAdapter(List<String> logs) {
        this.logs = logs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvLog.setText(logs.get(position));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    /**
     * Add a single log line. Should only be called on UI thread.
     */
    public void addLog(String log) {
        logs.add(log);
        if (logs.size() > MAX_LOGS) {
            int overflow = logs.size() - MAX_LOGS;
            for (int i = 0; i < overflow; i++) {
                logs.remove(0);
            }
            notifyDataSetChanged();
        } else {
            notifyItemInserted(logs.size() - 1);
        }
    }

    /**
     * Batch add logs to reduce RecyclerView refresh overhead.
     * Should only be called on UI thread.
     */
    public void addLogs(List<String> newLogs) {
        if (newLogs == null || newLogs.isEmpty()) {
            return;
        }
        logs.addAll(newLogs);
        if (logs.size() > MAX_LOGS) {
            int overflow = logs.size() - MAX_LOGS;
            for (int i = 0; i < overflow; i++) {
                logs.remove(0);
            }
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLog;

        ViewHolder(View itemView) {
            super(itemView);
            tvLog = itemView.findViewById(R.id.tv_log);
        }
    }
}
