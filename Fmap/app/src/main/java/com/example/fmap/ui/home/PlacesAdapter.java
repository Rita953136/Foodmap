package com.example.fmap.ui.home;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;

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
    private final OnPlaceClickListener clickListener;

    public interface OnPlaceClickListener {
        void onPlaceClick(Place place);
    }

    public PlacesAdapter(Context context, OnPlaceClickListener listener) {
        this.context = context.getApplicationContext();
        this.clickListener = listener;
    }

    public void submit(List<Place> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public Place getItem(int pos) {
        return (pos >= 0 && pos < data.size()) ? data.get(pos) : null;
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
        return new VH(v, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Place item = getItem(position);
        if (item != null) h.bind(item, context);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** 取前三個 tag，沒有就回空字串（或可改回 address 當備援） */
    private static String buildTagsPreferTagsFirst(Place p) {
        if (p == null) return "";
        List<String> tags = p.getTagsTop3();
        if (tags != null && !tags.isEmpty()) {
            List<String> show = tags.size() > 3 ? tags.subList(0, 3) : tags;
            return "#" + TextUtils.join(" #", show);
        }
        // 沒有標籤時的備援顯示：可改成 p.getAddress() 或價位等
        return "";
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb, ivStar;
        TextView tvName, tvRating, tvTags;
        TextView like, nope;

        private final OnPlaceClickListener listener;

        VH(@NonNull View v, OnPlaceClickListener listener) {
            super(v);
            this.listener = listener;
            imgThumb = v.findViewById(R.id.imgThumb);
            ivStar = v.findViewById(R.id.ivStar);
            tvName = v.findViewById(R.id.tvName);
            tvRating = v.findViewById(R.id.tvRating);
            tvTags = v.findViewById(R.id.tvTags);
            like = v.findViewById(R.id.badgeLike);
            nope = v.findViewById(R.id.badgeNope);
        }

        void bind(Place item, Context context) {
            // 名稱
            tvName.setText(item.getName() != null ? item.getName() : "");

            // 評分
            Double r = item.getRating();
            if (r != null && r > 0) {
                tvRating.setText(String.format(Locale.getDefault(), "%.1f", r));
                tvRating.setVisibility(View.VISIBLE);
                if (ivStar != null) ivStar.setVisibility(View.VISIBLE);
            } else {
                tvRating.setText("");
                tvRating.setVisibility(View.GONE);
                if (ivStar != null) ivStar.setVisibility(View.GONE);
            }

            // 標籤
            String tagsText = PlacesAdapter.buildTagsPreferTagsFirst(item);
            tvTags.setText(tagsText);
            tvTags.setVisibility(tagsText.isEmpty() ? View.GONE : View.VISIBLE);

            // 圖片（統一用 imageUrl，優先用 Place 的 FullPath，否則手動補資產前綴）
            String imageUrl = item.getCoverImageFullPath();
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                String raw = item.getCoverImage();
                if (raw != null && !raw.isEmpty() && !raw.startsWith("http") && !raw.startsWith("file:///")) {
                    imageUrl = "file:///android_asset/" + raw;     // e.g. stores/xxx/cover.jpg
                } else {
                    imageUrl = raw; // 可能是 http(s) 或 null
                }
            }

            Glide.with(context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(new ColorDrawable(Color.BLACK))
                    .error(new ColorDrawable(Color.BLACK))
                    .into(imgThumb);


            // 點擊事件
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    Place clicked = ((PlacesAdapter) getBindingAdapter()).getItem(pos);
                    if (clicked != null) listener.onPlaceClick(clicked);
                }
            });
        }

    }
}
