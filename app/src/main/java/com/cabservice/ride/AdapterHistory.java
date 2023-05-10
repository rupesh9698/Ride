package com.cabservice.ride;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdapterHistory extends RecyclerView.Adapter<AdapterHistory.HistoryViewHolders> {

    private final List<ModelHistory> itemList;

    public AdapterHistory(ArrayList<ModelHistory> itemList) {
        this.itemList = itemList;
    }

    @NonNull
    @Override
    public HistoryViewHolders onCreateViewHolder(ViewGroup parent, int viewType) {

        @SuppressLint("InflateParams") View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, null, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutView.setLayoutParams(lp);
        return new HistoryViewHolders(layoutView);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolders holder, int position) {
        String time = itemList.get(position).getTime();
        String rideId = itemList.get(position).getRideId();
        if (time != null && rideId != null) {
            holder.time.setText(itemList.get(position).getTime());
            holder.historyLl.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), HistorySingleActivity.class);
                Bundle b = new Bundle();
                b.putString("rideId", rideId);
                intent.putExtras(b);
                v.getContext().startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return this.itemList.size();
    }

    public static class HistoryViewHolders extends RecyclerView.ViewHolder {

        public final TextView time;
        public final LinearLayoutCompat historyLl;

        public HistoryViewHolders(@NonNull View itemView) {
            super(itemView);

            time = itemView.findViewById(R.id.time);
            historyLl = itemView.findViewById(R.id.historyLl);
        }
    }
}
