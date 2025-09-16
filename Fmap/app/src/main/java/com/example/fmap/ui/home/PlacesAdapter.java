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
        ImageView img; TextView tvName; TextView tvMeta; TextView tvTags; TextView like; TextView nope;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.imgThumb);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvTags = v.findViewById(R.id.tvTags);
            like = v.findViewById(R.id.badgeLike);
            nope = v.findViewById(R.id.badgeNope);
        }
        void bind(Place p) {
            tvName.setText(p.getName());
            String price = p.getPriceLevel() != null ? new String(new char[p.getPriceLevel()]).replace("\0", "$") : "-";
            String dist = p.getDistanceMeters() != null ? (" · " + p.getDistanceMeters() + "m") : "";
            String rating = p.getRating() != null ? String.valueOf(p.getRating()) : "-";
            tvMeta.setText("★ " + rating + " · " + price + dist);
            List<String> tags = p.getTags();
            tvTags.setText(tags == null ? "" : join(tags));
            like.setAlpha(0f); nope.setAlpha(0f);
            //if (p.getThumbnailUrl() != null) Glide.with(img.getContext()).load(p.getThumbnailUrl()).into(img);
            //else img.setImageDrawable(null);
            img.setImageResource(R.drawable.ic_launcher_foreground);
        }
        private String join(List<String> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size() && i < 3; i++) { if (i > 0) sb.append("  •  "); sb.append(list.get(i)); }
            return sb.toString();
        }
    }
}