package com.example.fmap.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;

import java.util.List;
import java.util.Objects;

public class TrashCardAdapter extends ListAdapter<Place, TrashCardAdapter.TrashCardVH> {

    public interface Listener {
        void onRestore(@NonNull Place p);
        void onDelete(@NonNull Place p);
    }

    private final Listener listener;

    public TrashCardAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrashCardVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                // 若你使用 item_disliked_card，改成 R.layout.item_disliked_card
                .inflate(R.layout.item_trash_card, parent, false);
        return new TrashCardVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TrashCardVH holder, int position) {
        Place p = getItem(position);
        holder.bind(p, listener);
    }

    public static class TrashCardVH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvName, tvRating, tvTags, btnRestore, btnDelete;

        public TrashCardVH(@NonNull View v) {
            super(v);
            img        = v.findViewById(R.id.imgThumb);
            tvName     = v.findViewById(R.id.tvName);
            tvRating   = v.findViewById(R.id.tvRating);
            tvTags     = v.findViewById(R.id.tvTags);
            btnRestore = v.findViewById(R.id.btnRestore);
            btnDelete  = v.findViewById(R.id.btnDelete);
        }

        public void bind(@NonNull final Place p, @NonNull final Listener listener) {
            tvName.setText(p.name != null ? p.name : "(無名稱)");
            tvRating.setText(p.rating != null ? String.valueOf(p.rating) : "-");
            if (p.tags != null && !p.tags.isEmpty()) {
                tvTags.setText(android.text.TextUtils.join("、", p.tags));
            } else {
                tvTags.setText("");
            }

            if (p.photoUrl != null && !p.photoUrl.isEmpty()) {
                // 如果有用 Glide/Picasso：在此載入縮圖
                // Glide.with(img.getContext()).load(p.photoUrl).into(img);
            } else {
                img.setImageResource(android.R.color.darker_gray);
            }

            btnRestore.setOnClickListener(v -> listener.onRestore(p));
            btnDelete.setOnClickListener(v -> listener.onDelete(p));
        }
    }

    private static final DiffUtil.ItemCallback<Place> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Place>() {
                @Override
                public boolean areItemsTheSame(@NonNull Place oldItem, @NonNull Place newItem) {
                    // 用 id 判斷是否同一項
                    return Objects.equals(oldItem.id, newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Place oldItem, @NonNull Place newItem) {
                    // 若 Place 沒有覆寫 equals，逐欄位比對
                    if (!Objects.equals(oldItem.name, newItem.name)) return false;
                    if (!Objects.equals(oldItem.rating, newItem.rating)) return false;
                    if (!Objects.equals(oldItem.photoUrl, newItem.photoUrl)) return false;

                    List<String> ot = oldItem.tags, nt = newItem.tags;
                    if (ot == null && nt == null) return true;
                    if (ot == null || nt == null) return false;
                    if (ot.size() != nt.size()) return false;
                    for (int i = 0; i < ot.size(); i++) {
                        if (!Objects.equals(ot.get(i), nt.get(i))) return false;
                    }
                    return true;
                }
            };
}
