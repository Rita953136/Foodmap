package com.example.fmap.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.model.FavItem;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.VH> {

    public interface OnHeartClick {
        void onHeart(int position, FavItem item);
    }

    public interface OnItemClick {
        void onItem(int position, FavItem item);
    }

    private final List<FavItem> items = new ArrayList<>();
    private final OnHeartClick cb;
    private static OnItemClick onItem = null;

    public FavoriteAdapter(OnHeartClick cb, OnItemClick onItem) {
        this.cb = cb;
        this.onItem = onItem;
    }

    public void submit(List<FavItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, items.size() - position);
    }

    public FavItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
        return new VH(v, cb);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView name;
        TextView meta;
        TextView tags;
        ImageButton heart;
        OnHeartClick cb;

        VH(@NonNull View v, OnHeartClick cb) {
            super(v);
            this.cb = cb;
            img = v.findViewById(R.id.imgThumb);
            name = v.findViewById(R.id.tvName);
            meta = v.findViewById(R.id.tvMeta);
            tags = v.findViewById(R.id.tvTags);
            heart = v.findViewById(R.id.btnHeart);
        }

        void bind(FavItem p) {
            name.setText(p.name != null ? p.name : "");

            // --- 安全處理 meta 欄位 ---
            // 處理價位：<=0 或 null 都視為無價位
            int level = (p.priceLevel == null || p.priceLevel <= 0) ? 0 : p.priceLevel;
            String price;
            if (level == 0) {
                price = "-";
            } else {
                char[] dollars = new char[level];
                java.util.Arrays.fill(dollars, '$');
                price = new String(dollars);
            }

            String rating = (p.rating != null && p.rating >= 0) ? String.valueOf(p.rating) : "-";
            String dist = (p.distanceMeters != null && p.distanceMeters > 0) ? (" · " + p.distanceMeters + "m") : "";

            meta.setText("★ " + rating + " · " + price + dist);

            if (p.tags != null && !p.tags.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, p.tags.size()); i++) {
                    if (i > 0) sb.append("  •  ");
                    sb.append(p.tags.get(i));
                }
                tags.setText(sb.toString());
            } else {
                tags.setText("");
            }

            if (p.thumbnailUrl != null && !p.thumbnailUrl.isEmpty()) {
                Glide.with(img.getContext()).load(p.thumbnailUrl).into(img);
            } else {
                img.setImageDrawable(null);
            }

            // 右側愛心（取消收藏）
            heart.setImageResource(R.drawable.ic_favorite_filled);
            heart.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && cb != null) cb.onHeart(pos, p);
            });

            // 點整列進詳細
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && cb != null && onItem != null)
                    onItem.onItem(pos, p);
            });
        }

    }
}
