package com.example.fmap.ui.home;

import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;

import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private HomeViewModel vm;
    private PlacesAdapter adapter;
    private LinearLayout emptyView;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        vm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        RecyclerView rv = v.findViewById(R.id.rvCards);
        emptyView = v.findViewById(R.id.emptyView);
        TextView tvEmpty = v.findViewById(R.id.tvEmpty);
        Button btnGoSearch = v.findViewById(R.id.btnGoSearch);
        Button btnDevReset = v.findViewById(R.id.btnDevReset);

        boolean isDebug = (requireContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        btnDevReset.setVisibility(isDebug ? View.VISIBLE : View.GONE);
        btnDevReset.setOnClickListener(view -> {
            vm.devResetQuotaAndReload();
            Toast.makeText(requireContext(), "已重置今日額度", Toast.LENGTH_SHORT).show();
        });
        tvEmpty.setOnLongClickListener(view -> {
            vm.devResetQuotaAndReload();
            Toast.makeText(requireContext(), "已重置今日額度並補上新推薦", Toast.LENGTH_SHORT).show();
            return true;
        });
        btnGoSearch.setOnClickListener(view ->
                Toast.makeText(requireContext(), "前往搜尋（待實作）", Toast.LENGTH_SHORT).show());

        adapter = new PlacesAdapter(position -> {});
        rv.setLayoutManager(new LinearLayoutManager(requireContext()) {
            @Override public boolean canScrollVertically() { return false; }
        });
        rv.setAdapter(adapter);

        // 使用假資料，顯示 20 張一樣的卡片
        adapter.submit(com.example.fmap.model.FakeData.fakePlaces(20));

        ItemTouchHelper.SimpleCallback cb = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            private static final float MAX_ROTATION = 15f;
            private static final float SWIPE_THRESHOLD = 0.25f;

            @Override public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, vh, dX, dY, actionState, isCurrentlyActive);
                float width = Math.max(1f, rv.getWidth());
                float progress = Math.max(-1f, Math.min(1f, dX / width));
                vh.itemView.setRotation(progress * MAX_ROTATION);

                if (vh instanceof PlacesAdapter.VH) {
                    PlacesAdapter.VH holder = (PlacesAdapter.VH) vh;
                    holder.like.setAlpha(Math.max(0f, progress));
                    holder.nope.setAlpha(Math.max(0f, -progress));
                }

                int pos = vh.getAdapterPosition();
                RecyclerView.ViewHolder nextVH = rv.findViewHolderForAdapterPosition(pos + 1);
                if (nextVH != null) {
                    float scale = 0.94f + 0.06f * Math.min(1f, Math.abs(progress));
                    nextVH.itemView.setScaleX(scale);
                    nextVH.itemView.setScaleY(scale);
                    nextVH.itemView.setTranslationY(-20f * Math.min(1f, Math.abs(progress)));
                }
            }

            @Override public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) { return SWIPE_THRESHOLD; }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(recyclerView, vh);
                vh.itemView.setRotation(0f);
                if (vh instanceof PlacesAdapter.VH) {
                    PlacesAdapter.VH holder = (PlacesAdapter.VH) vh;
                    holder.like.setAlpha(0f);
                    holder.nope.setAlpha(0f);
                }
                int pos = vh.getAdapterPosition();
                RecyclerView.ViewHolder nextVH = rv.findViewHolderForAdapterPosition(pos + 1);
                if (nextVH != null) {
                    nextVH.itemView.setScaleX(0.94f);
                    nextVH.itemView.setScaleY(0.94f);
                    nextVH.itemView.setTranslationY(0f);
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                Place item = adapter.getItem(pos);
                if (direction == ItemTouchHelper.RIGHT) vm.onSwipedRight(item);
                else vm.onSwipedLeft(item);
                adapter.removeAt(pos);
                updateEmptyState();
            }
        };
        new ItemTouchHelper(cb).attachToRecyclerView(rv);

        vm.getPlaces().observe(getViewLifecycleOwner(), new Observer<List<Place>>() {
            @Override public void onChanged(List<Place> places) {
                adapter.submit(places != null ? places : Collections.<Place>emptyList());
                updateEmptyState();
            }
        });
        vm.getExhausted().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override public void onChanged(Boolean usedUp) {
                if (usedUp != null && usedUp) updateEmptyState();
            }
        });

        vm.loadRecommendations();
    }

    private void updateEmptyState() {
        emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
