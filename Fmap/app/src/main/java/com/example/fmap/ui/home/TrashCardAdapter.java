package com.example.fmap.ui.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrashCardAdapter extends RecyclerView.Adapter<TrashCardAdapter.VH> {

    public interface OnTrashActionListener {
        void onRestore(@NonNull Place place, int position);
    }

    private final List<Place> data = new ArrayList<>();
    private final Context context;
    private final OnTrashActionListener listener;

    public TrashCardAdapter(@NonNull Context ctx, @NonNull OnTrashActionListener l) {
        this.context = ctx.getApplicationContext();
        this.listener = l;
    }

    public void submit(List<Place> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void removeAt(int pos) {
        if (pos >= 0 && pos < data.size()) {
            data.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public Place getItem(int pos) {
        return (pos >= 0 && pos < data.size()) ? data.get(pos) : null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trash_card, parent, false);
        return new VH(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Place p = getItem(position);
        if (p != null) holder.bind(p, context);
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        RatingBar ratingBar;
        TextView tvName, tvRating, tvTags;
        MaterialButton btnRestore;

        private final OnTrashActionListener listener;

        VH(@NonNull View v, OnTrashActionListener l) {
            super(v);
            listener = l;
            imgThumb = v.findViewById(R.id.imgThumb);
            ratingBar = v.findViewById(R.id.ratingBar);
            tvName   = v.findViewById(R.id.tvName);
            tvRating = v.findViewById(R.id.tvRating);
            tvTags   = v.findViewById(R.id.tvTags);
            btnRestore = v.findViewById(R.id.btnRestore);
        }

        void bind(@NonNull Place p, @NonNull Context ctx) {
            tvName.setText(p.getName() != null ? p.getName() : "");

            Double r = p.getRating();
            if (r != null && r > 0) {
                tvRating.setText(String.format(Locale.getDefault(), "%.1f", r));
                tvRating.setVisibility(View.VISIBLE);

                if (ratingBar != null) {
                    ratingBar.setRating(r.floatValue());
                    ratingBar.setVisibility(View.VISIBLE);
                }
            } else {
                tvRating.setText("");
                tvRating.setVisibility(View.GONE);
                if (ratingBar != null) {
                    ratingBar.setVisibility(View.GONE);
                }
            }

            List<String> tags = p.getTagsTop3();
            if (tags != null && !tags.isEmpty()) {
                tvTags.setVisibility(View.VISIBLE);
                tvTags.setText("#" + TextUtils.join(" #", tags.size() > 3 ? tags.subList(0, 3) : tags));
            } else {
                tvTags.setVisibility(View.GONE);
            }

            String imageUrl = p.getCoverImageFullPath();
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                String raw = p.getCoverImage();
                if (raw != null && !raw.isEmpty() && !raw.startsWith("http") && !raw.startsWith("file:///")) {
                    imageUrl = "file:///android_asset/" + raw;
                } else {
                    imageUrl = raw;
                }
            }

            Glide.with(ctx)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.color.material_dynamic_neutral90)
                    .error(R.color.material_dynamic_neutral90)
                    .into(imgThumb);

            btnRestore.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) listener.onRestore(p, pos);
                }
            });
        }
    }
}
