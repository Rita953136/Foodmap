package com.example.fmap.ui.home;

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

public class PlacesAdapter extends RecyclerView.Adapter<PlacesAdapter.VH> {

    public interface OnBindIndexListener { void onBind(int position); }

    private final OnBindIndexListener onBindIndex;
    private final List<Place> items = new ArrayList<>();

    public PlacesAdapter(OnBindIndexListener onBindIndex) { this.onBindIndex = onBindIndex; }
    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
        // 讓後面的位置重新綁定，避免位置錯亂
        notifyItemRangeChanged(position, items.size() - position);
    }

    public void submit(List<Place> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public Place getItem(int position) { return items.get(position); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place_card, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        onBindIndex.onBind(position);
        holder.bind(items.get(position));

        // 疊層視覺：頂層 1.0；下一張 0.94；再下一張 0.91（最多到第 2 層就好）
        int depth = Math.min(2, position);        // 0,1,2
        float scale = 1f - (0.03f * depth);       // 1.00, 0.97, 0.94（可依喜好調）
        float translate = 12f * depth;            // 每層往下 12dp 視覺（這裡用 px，可再把 dp->px）
        holder.itemView.setScaleX(scale);
        holder.itemView.setScaleY(scale);
        holder.itemView.setTranslationY(translate);

        // 頂層的 like/nope 預設透明
        if (depth == 0) { holder.like.setAlpha(0f); holder.nope.setAlpha(0f); }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvName;
        TextView tvMeta;   // 這版不使用，預設會 GONE
        TextView tvTags;   // 第二行：#Tag · $$ · 距離
        TextView like, nope;

        // 右上角評分
        ImageView ivStar;
        TextView tvRating;

        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.imgThumb);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvTags = v.findViewById(R.id.tvTags);
            like = v.findViewById(R.id.badgeLike);
            nope = v.findViewById(R.id.badgeNope);
            ivStar = v.findViewById(R.id.ivStar);
            tvRating = v.findViewById(R.id.tvRating);
        }

        void bind(Place p) {
            // 第一行：店名（左）
            tvName.setText(p.getName() != null ? p.getName() : "");

            // 第一行：評分（右）
            Double rt = p.getRating();
            if (rt != null && rt >= 0) {
                tvRating.setText(String.valueOf(rt));
                ivStar.setVisibility(View.VISIBLE);
                tvRating.setVisibility(View.VISIBLE);
            } else {
                ivStar.setVisibility(View.GONE);
                tvRating.setVisibility(View.GONE);
            }

            // 第二行：#Tag1 #Tag2 #Tag3 · $$ · 500公尺
            String price = dollars(p.getPriceLevel());           // "$$", or "-"
            String dist  = metersToHuman(p.getDistanceMeters()); // "500公尺" 或 ""
            java.util.List<String> tags = p.getTags();

            StringBuilder line2 = new StringBuilder();
            if (tags != null && !tags.isEmpty()) {
                for (int i = 0; i < Math.min(3, tags.size()); i++) {
                    if (i > 0) line2.append(' ');
                    line2.append('#').append(tags.get(i));
                }
            }
            if (!"-".equals(price)) {
                if (line2.length() > 0) line2.append(" · ");
                line2.append(price);
            }
            if (!dist.isEmpty()) {
                if (line2.length() > 0) line2.append(" · ");
                line2.append(dist);
            }

            if (line2.length() == 0) {
                tvTags.setText("");
                tvTags.setVisibility(View.GONE);   // 沒內容就收起來
            } else {
                tvTags.setText(line2.toString());
                tvTags.setVisibility(View.VISIBLE);
            }

            // 這個版型不使用 tvMeta
            tvMeta.setVisibility(View.GONE);

            // 圖片（目前用假圖；要真圖就用 Glide 載入 thumbnailUrl）
            // if (p.getThumbnailUrl() != null) Glide.with(img.getContext()).load(p.getThumbnailUrl()).into(img);
            // else img.setImageDrawable(null);
            img.setImageResource(R.drawable.ic_launcher_foreground);

            // 貼紙預設透明
            like.setAlpha(0f);
            nope.setAlpha(0f);
        }

        private static String dollars(Integer level) {
            if (level == null || level <= 0) return "-";
            char[] c = new char[level];
            java.util.Arrays.fill(c, '$');
            return new String(c);
        }

        private static String metersToHuman(Integer m) {
            if (m == null || m <= 0) return "";
            // 想 >=1000 顯示公里可改：
            // if (m >= 1000) return new java.text.DecimalFormat("#.0").format(m / 1000.0) + "公里";
            return m + "公尺";
        }
    }
}