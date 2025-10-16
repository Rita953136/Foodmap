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

/**
 * 用於顯示收藏店家列表的 RecyclerView Adapter
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final List<Place> favoriteList = new ArrayList<>();
    private final OnFavoriteClickListener listener;
    private final Context context;

    /**
     * 定義點擊事件的介面 (Interface)
     */
    public interface OnFavoriteClickListener {
        void onItemClick(Place place);
        void onHeartClick(Place place, int position);
    }

    /**
     * Adapter 的建構子
     * @param context  Context，用於 Glide
     * @param listener 點擊事件的監聽器
     */
    public FavoriteAdapter(Context context, @NonNull OnFavoriteClickListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /**
     * 更新 Adapter 的資料列表
     * @param newPlaces 新的店家列表
     */
    public void submitList(List<Place> newPlaces) {
        favoriteList.clear();
        if (newPlaces != null) {
            favoriteList.addAll(newPlaces);
        }
        // 通知 RecyclerView 資料已變更
        notifyDataSetChanged();
    }

    /**
     * 從列表中移除一個項目
     * @param position 要移除的項目位置
     */
    public void removeItem(int position) {
        if (position >= 0 && position < favoriteList.size()) {
            favoriteList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, favoriteList.size()); // 更新後續項目的位置
        }
    }


    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 載入 item_favorite.xml 作為列表項目的佈局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        // 取得目前位置的店家資料
        Place currentPlace = favoriteList.get(position);
        // 綁定資料到 View 上
        holder.bind(currentPlace, context, listener);
    }

    @Override
    public int getItemCount() {
        return favoriteList.size();
    }


    /**
     * ViewHolder 類別，負責管理 item_favorite.xml 中的所有 View
     */
    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        // 對應 item_favorite.xml 中的所有 View
        private final ImageView imgThumb;
        private final TextView tvName;
        private final TextView tvMeta;
        private final TextView tvTags;
        private final ImageButton btnHeart;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            // 初始化 View
            imgThumb = itemView.findViewById(R.id.imgThumb);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvTags = itemView.findViewById(R.id.tvTags);
            btnHeart = itemView.findViewById(R.id.btnHeart);
        }

        /**
         * 將 Place 物件的資料綁定到 UI 上
         */
        public void bind(final Place place, Context context, final OnFavoriteClickListener listener) {
            // 設定店家名稱
            tvName.setText(place.name);

            // 設定評分和地址等元資訊
            String metaText = "";
            if (place.rating != null && place.rating > 0) {
                metaText = String.format(Locale.getDefault(), "%.1f ★", place.rating);
            }
            if (place.address != null && !place.address.isEmpty()) {
                // 如果已有評分，加上分隔符號
                if (!metaText.isEmpty()) {
                    metaText += " ・ ";
                }
                metaText += place.address;
            }
            tvMeta.setText(metaText);
            tvMeta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);


            // 設定標籤
            if (place.tags != null && !place.tags.isEmpty()) {
                tvTags.setText("#" + TextUtils.join(" #", place.tags));
                tvTags.setVisibility(View.VISIBLE);
            } else {
                tvTags.setVisibility(View.GONE);
            }

            // 使用 Glide 載入圖片
            Glide.with(context)
                    .load(place.photoUrl)
                    .centerCrop()
                    .placeholder(R.color.material_dynamic_neutral80) // 預設的灰色背景
                    .error(R.color.material_dynamic_neutral80) // 載入失敗時的背景
                    .into(imgThumb);

            // 設定整個項目的點擊監聽器
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(place);
                }
            });

            // 設定愛心按鈕的點擊監聽器
            btnHeart.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onHeartClick(place, getBindingAdapterPosition());
                }
            });
        }
    }
}
