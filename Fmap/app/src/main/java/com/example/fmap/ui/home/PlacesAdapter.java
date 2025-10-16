package com.example.fmap.ui.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class PlacesAdapter extends RecyclerView.Adapter<PlacesAdapter.VH> {

    private final List<Place> data = new ArrayList<>();
    private final Context context;
    // ✨ 1. 新增點擊監聽器介面和成員變數
    private final OnPlaceClickListener clickListener;

    // ✨ 2. 定義點擊監聽器介面
    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    // ✨ 3. 修改建構子，要求傳入監聽器
    public PlacesAdapter(Context context, OnPlaceClickListener listener) {
        this.context = context;
        this.clickListener = listener; // 儲存監聽器
    }

    public void submit(List<Place> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    public Place getItem(int pos) {
        if (pos >= 0 && pos < data.size()) {
            return data.get(pos);
        }
        return null;
    }

    public void removeAt(int pos) {
        if (pos >= 0 && pos < data.size()) {
            data.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_card, parent, false);
        // ✨ 4. 將監聽器傳遞給 ViewHolder
        return new VH(v, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Place item = getItem(position);
        if (item == null) return;
        h.bind(item, context);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static String buildTagsPreferTagsFirst(Place p) {
        if (p != null && p.tags != null && !p.tags.isEmpty()) {
            List<String> tagsToShow = p.tags.size() > 3 ? p.tags.subList(0, 3) : p.tags;
            return "#" + TextUtils.join(" #", tagsToShow);
        }
        return p != null && p.introLine != null ? p.introLine : "";
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb, ivStar;
        TextView tvName, tvRating, tvTags;
        TextView like, nope;

        // ✨ 5. ViewHolder 建構子也接收監聽器
        VH(@NonNull View v, OnPlaceClickListener listener) {
            super(v);
            imgThumb = v.findViewById(R.id.imgThumb);
            ivStar = v.findViewById(R.id.ivStar);
            tvName = v.findViewById(R.id.tvName);
            tvRating = v.findViewById(R.id.tvRating);
            tvTags = v.findViewById(R.id.tvTags);
            like = v.findViewById(R.id.badgeLike);
            nope = v.findViewById(R.id.badgeNope);
        }

        // ✨ 6. 新增 bind 方法來綁定資料和點擊事件
        void bind(Place item, Context context) {
            tvName.setText(item.name != null ? item.name : "");
            double ratingVal = item.rating != null ? item.rating : 0d;
            tvRating.setText(String.format(Locale.getDefault(), "%.1f", ratingVal));
            tvTags.setText(buildTagsPreferTagsFirst(item));

            Glide.with(context)
                    .load(item.photoUrl)
                    .placeholder(R.drawable.outline_map_search_24)
                    .error(R.drawable.outline_map_search_24)
                    .centerCrop()
                    .into(imgThumb);

            // 在這裡設定點擊事件，並透過介面回傳
            itemView.setOnClickListener(v -> {
                // 從 Adapter 中獲取最新的資料物件
                // 這比直接使用 bind 方法傳入的 item 更安全，因為 ViewHolder 可能被複用
                int currentPosition = getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    Place clickedPlace = ((PlacesAdapter) getBindingAdapter()).getItem(currentPosition);
                    if(clickedPlace != null) {
                        // listener.onPlaceClick(item); // 直接使用 bind 傳入的 item 也可以
                        ((PlacesAdapter) getBindingAdapter()).clickListener.onPlaceClick(clickedPlace);
                    }
                }
            });
        }
    }
}
