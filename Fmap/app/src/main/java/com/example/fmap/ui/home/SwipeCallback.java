package com.example.fmap.ui.home;

import android.graphics.Canvas;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;  // << 新增：改用單檔 Swipe

/**
 * 左右滑卡片的 Callback：
 *  - 右滑：LIKE；左滑：NOPE
 *  - 滑動中同步顯示 badgeLike / badgeNope
 *  - 放手超過門檻：回調一筆 Swipe 並移除卡片
 *  - 放手未達門檻：卡片回彈、貼紙隱藏
 */
public class SwipeCallback extends ItemTouchHelper.SimpleCallback {

    /** 外部可實作此介面取得滑動紀錄（寫入本地或 Firestore） */
    public interface OnRecordListener {
        void onRecorded(@NonNull Swipe record, int adapterPosition); // << 型別改成 Swipe
    }

    // ---- 可調參數 ----
    private static final float SWIPE_THRESHOLD = 0.35f; // 超過卡片寬 35% 判定滑出
    private static final float MAX_ROTATION   = 8f;     // 滑動時的最大旋轉角度

    private final PlacesAdapter adapter;
    private final OnRecordListener listener;

    public SwipeCallback(@NonNull PlacesAdapter adapter,
                         @NonNull OnRecordListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT); // 啟用左右滑
        this.adapter = adapter;
        this.listener = listener;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        // 不支援拖曳排序
        return false;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return SWIPE_THRESHOLD; // 調整滑出門檻
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return;

        Place p = adapter.getItem(pos);
        Swipe.Action action = (direction == ItemTouchHelper.RIGHT)
                ? Swipe.Action.LIKE : Swipe.Action.NOPE;   // << 改用 Swipe.Action

        // 回報一筆滑動紀錄
        if (listener != null && p != null) {
            listener.onRecorded(
                    new Swipe(p.id, action, System.currentTimeMillis()), // << 建立 Swipe
                    pos
            );
        }

        // 預設：移除卡片；若只想回彈請改成 notifyItemChanged(pos)
        adapter.removeAt(pos);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {

        if (!(vh instanceof PlacesAdapter.VH)) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        PlacesAdapter.VH h = (PlacesAdapter.VH) vh;
        View item = vh.itemView;

        float w = item.getWidth();
        float progress = Math.min(1f, Math.abs(dX) / (w * SWIPE_THRESHOLD)); // 0~1

        // 依方向顯示貼紙透明度
        if (dX > 0) { // 右滑 → LIKE
            if (h.like != null) h.like.setAlpha(progress);
            if (h.nope != null) h.nope.setAlpha(0f);
        } else if (dX < 0) { // 左滑 → NOPE
            if (h.nope != null) h.nope.setAlpha(progress);
            if (h.like != null) h.like.setAlpha(0f);
        } else {
            if (h.like != null) h.like.setAlpha(0f);
            if (h.nope != null) h.nope.setAlpha(0f);
        }

        // 輕微旋轉 + 位移（更有卡片感）
        item.setRotation(MAX_ROTATION * (dX / w));
        item.setTranslationX(dX);

        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);

        // 放手但未過門檻 → 回彈並隱藏貼紙
        if (!isCurrentlyActive && Math.abs(dX) < w * SWIPE_THRESHOLD) {
            item.animate()
                    .translationX(0f)
                    .rotation(0f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (h.like != null) h.like.setAlpha(0f);
                        if (h.nope != null) h.nope.setAlpha(0f);
                    })
                    .start();
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        // 復位，避免回收重用殘留狀態
        View item = vh.itemView;
        if (item != null) {
            item.setTranslationX(0f);
            item.setRotation(0f);
        }
        if (vh instanceof PlacesAdapter.VH) {
            PlacesAdapter.VH h = (PlacesAdapter.VH) vh;
            if (h.like != null) h.like.setAlpha(0f);
            if (h.nope != null) h.nope.setAlpha(0f);
        }
    }
}
