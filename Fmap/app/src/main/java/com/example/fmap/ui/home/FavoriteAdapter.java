package com.example.fmap.ui.home;

import android.content.Context;
import android.text.TextUtils;
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
import com.example.fmap.model.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 用於顯示收藏店家列表的 RecyclerView Adapter */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final List<Place> favoriteList = new ArrayList<>();
    private final OnFavoriteClickListener listener;
    private final Context context;

    public interface OnFavoriteClickListener {
        void onItemClick(Place place);
        void onHeartClick(Place place, int position);
    }

    public FavoriteAdapter(Context context, @NonNull OnFavoriteClickListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void submitList(List<Place> newPlaces) {
        favoriteList.clear();
        if (newPlaces != null) favoriteList.addAll(newPlaces);
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < favoriteList.size()) {
            favoriteList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, favoriteList.size());
        }
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        holder.bind(favoriteList.get(position), context, listener);
    }

    @Override
    public int getItemCount() { return favoriteList.size(); }

    /** ViewHolder：綁定 item_favorite.xml 的元件 */
    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgThumb;
        private final TextView tvName;
        private final TextView tvMeta;
        private final TextView tvTags;
        private final ImageButton btnHeart;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.imgThumb);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvTags = itemView.findViewById(R.id.tvTags);
            btnHeart = itemView.findViewById(R.id.btnHeart);
        }

        public void bind(final Place place, Context context, final OnFavoriteClickListener listener) {
            // 名稱
            tvName.setText(place.getName());

            // 評分 + 地址
            StringBuilder meta = new StringBuilder();
            if (place.getRating() != null && place.getRating() > 0) {
                meta.append(String.format(Locale.getDefault(), "%.1f ★", place.getRating()));
            }
            if (place.getAddress() != null && !place.getAddress().isEmpty()) {
                if (meta.length() > 0) meta.append(" ・ ");
                meta.append(place.getAddress());
            }
            String metaText = meta.toString();
            tvMeta.setText(metaText);
            tvMeta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);

            // 標籤（使用 Place.getTagsTop3()）
            List<String> tags = place.getTagsTop3();
            if (tags != null && !tags.isEmpty()) {
                tvTags.setText("#" + TextUtils.join(" #", tags));
                tvTags.setVisibility(View.VISIBLE);
            } else {
                tvTags.setVisibility(View.GONE);
            }

            // 圖片
            String coverFullPath = place.getCoverImageFullPath(); // <--- 把 getCoverImage() 改成這個！
            Glide.with(context)
                    .load(coverFullPath) // <--- 使用這個完整路徑來載入
                    .centerCrop()
                    .placeholder(R.color.material_dynamic_neutral80)
                    .error(R.color.material_dynamic_neutral80)
                    .into(imgThumb);

            // 點擊事件
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(place);
            });
            btnHeart.setOnClickListener(v -> {
                if (listener != null) listener.onHeartClick(place, getBindingAdapterPosition());
            });
        }
    }
}
