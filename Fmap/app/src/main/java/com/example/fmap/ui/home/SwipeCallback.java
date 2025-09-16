package com.example.fmap.ui.home;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.model.Place;

public class SwipeCallback extends ItemTouchHelper.SimpleCallback {

    public interface OnSwipedListener {
        void onSwipedLeft(Place p);
        void onSwipedRight(Place p);
        void onSlide(int position, float dX);
    }

    private final RecyclerView recyclerView;
    private final PlacesAdapter adapter;
    private final OnSwipedListener listener;

    public SwipeCallback(RecyclerView rv, PlacesAdapter adapter, OnSwipedListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.recyclerView = rv; this.adapter = adapter; this.listener = listener;
    }

    @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) { return false; }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
        int pos = vh.getAdapterPosition();
        listener.onSlide(pos, dX); // 讓外面決定 LIKE/NOPE 透明度等效果
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getAdapterPosition();
        Place item = adapter.getItem(pos);
        if (direction == ItemTouchHelper.RIGHT) listener.onSwipedRight(item);
        else listener.onSwipedLeft(item);
        adapter.notifyItemRemoved(pos);
    }
}
