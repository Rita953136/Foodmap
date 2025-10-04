package com.example.fmap.ui.home;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlacesAdapter extends RecyclerView.Adapter<PlacesAdapter.VH> {

    public interface OnAction { void onClick(int position); }

    private final OnAction action;
    private final List<Place> data = new ArrayList<>();

    public PlacesAdapter(OnAction action) { this.action = action; }

    public void submit(List<Place> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public Place getItem(int pos) { return data.get(pos); }

    public void removeAt(int pos) {
        if (pos >= 0 && pos < data.size()) {
            data.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Place item = data.get(position);

        // 店名
        h.tvName.setText(item.name != null ? item.name : "");

        // 評分
        double ratingVal = item.rating != null ? item.rating : 0d;
        h.tvRating.setText(String.format(Locale.getDefault(), "%.1f", ratingVal));

        // ✅ 第二行：優先顯示 tags，沒有 tags 才顯示 intro
        h.tvTags.setText(buildTagsPreferTagsFirst(item));

        // 圖片（之後可用 Glide）
        // Glide.with(h.itemView).load(item.photoUrl).into(h.imgThumb);

        h.itemView.setOnClickListener(v -> {
            if (action != null) action.onClick(h.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    private static String buildTagsPreferTagsFirst(Place p) {
        if (p != null && p.tags != null && !p.tags.isEmpty()) {
            return "#" + TextUtils.join(" #", p.tags);
        }
        return p != null && p.introLine != null ? p.introLine : "";
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb, ivStar;
        TextView tvName, tvRating, tvTags;
        TextView like, nope;

        VH(@NonNull View v) {
            super(v);
            imgThumb = v.findViewById(R.id.imgThumb);
            ivStar   = v.findViewById(R.id.ivStar);
            tvName   = v.findViewById(R.id.tvName);
            tvRating = v.findViewById(R.id.tvRating);
            tvTags   = v.findViewById(R.id.tvTags);
            like     = v.findViewById(R.id.badgeLike);
            nope     = v.findViewById(R.id.badgeNope);
        }
    }
}
