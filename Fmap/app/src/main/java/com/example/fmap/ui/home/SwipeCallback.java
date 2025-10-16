package com.example.fmap.ui.home;

import android.graphics.Canvas;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.model.Swipe; // ✨ 1. 匯入你的 Swipe CLASS

/**
 * RecyclerView 的滑動回呼處理
 * 這個版本會回傳 Swipe.Action enum，而不是舊的 Swipe enum
 */
public class SwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final PlacesAdapter adapter;
    private final OnSwipedListener listener;
    public interface OnSwipedListener {
        void onSwiped(Swipe.Action action, int position);
    }

    public SwipeCallback(PlacesAdapter adapter, OnSwipedListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false; // 我們不處理拖曳移動
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && listener != null) {
            // ✨ 3. 根據滑動方向，回傳對應的 Swipe.Action
            Swipe.Action action = (direction == ItemTouchHelper.RIGHT) ? Swipe.Action.LIKE : Swipe.Action.NOPE;
            listener.onSwiped(action, position);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            PlacesAdapter.VH vh = (PlacesAdapter.VH) viewHolder;
            float alpha = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
            viewHolder.itemView.setAlpha(alpha);
            viewHolder.itemView.setTranslationX(dX);

            // 根據滑動方向顯示 LIKE 或 NOPE 徽章
            if (dX > 0) { // 向右滑
                vh.like.setAlpha(1.0f);
                vh.nope.setAlpha(0f);
            } else { // 向左滑
                vh.like.setAlpha(0f);
                vh.nope.setAlpha(1.0f);
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // 當滑動被取消或完成時，恢復 item 的外觀
        viewHolder.itemView.setAlpha(1.0f);
        ((PlacesAdapter.VH) viewHolder).like.setAlpha(0f);
        ((PlacesAdapter.VH) viewHolder).nope.setAlpha(0f);
    }
}
