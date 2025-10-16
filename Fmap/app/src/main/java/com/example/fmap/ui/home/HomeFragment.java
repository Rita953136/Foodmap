package com.example.fmap.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
/**
 * App 的主頁，負責顯示推薦卡片和處理使用者互動。
 * 觀察標籤變化並觸發重載
 */
public class HomeFragment extends Fragment implements PlacesAdapter.OnPlaceClickListener {
    private static final String TAG = "HomeFragment";
    private RecyclerView rvCards;
    private View emptyView;
    private TextView tvEmpty;
    private ProgressBar loadingIndicator;
    private PlacesAdapter adapter;
    private HomeViewModel homeViewModel;
    private ChipGroup chipGroupTags;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setHomeToolbar();
        }

        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        initializeViews(v);
        setupRecyclerView();
        observeViewModel();
        setupTagMultiSelect();
        // Fragment 建立時，檢查是否需要初次載入
        if (homeViewModel.getPlaces().getValue() == null) {
            homeViewModel.loadPlaces();
        }
    }

    private void setupTagMultiSelect() {
        if (chipGroupTags == null) return;

        // 關鍵：用群組狀態變更事件收集所有被勾選的 chip
        chipGroupTags.setOnCheckedStateChangeListener((group, checkedIds) -> {
            java.util.List<String> selected = new java.util.ArrayList<>();
            for (int id : checkedIds) {
                Chip chip = group.findViewById(id);
                if (chip != null && chip.isChecked()) {
                    selected.add(chip.getText().toString());
                }
            }
            // 直接把多選結果丟給 ViewModel，ViewModel 會重新查詢（ANY/ALL 規則看你用哪版 VM）
            homeViewModel.applyTagFilter(selected);
        });
    }


    // initializeViews 和 setupRecyclerView 方法保持不變
    private void initializeViews(@NonNull View view) {
        rvCards = view.findViewById(R.id.rvCards);
        emptyView = view.findViewById(R.id.emptyView);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        chipGroupTags = view.findViewById(R.id.chip_group_tags);
    }
    private void setupRecyclerView() {
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setHasFixedSize(true);
        adapter = new PlacesAdapter(requireContext(), this);
        rvCards.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new SwipeCallback(adapter, (swipeAction, pos) -> {
                    Place swipedPlace = adapter.getItem(pos);
                    if (swipedPlace == null) return;
                    adapter.removeAt(pos);
                    homeViewModel.handleSwipeAction(swipeAction, swipedPlace);
                    if (swipeAction == Swipe.Action.LIKE) {
                        Toast.makeText(getContext(), "已收藏：" + swipedPlace.name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "已將 " + swipedPlace.name + " 加入不喜歡列表", Toast.LENGTH_SHORT).show();
                    }
                })
        );
        itemTouchHelper.attachToRecyclerView(rvCards);
    }

    private void observeViewModel() {
        homeViewModel.getSelectedTags().observe(getViewLifecycleOwner(), tags -> {
            Log.d(TAG, "標籤已更新，當前篩選條件: " + tags);
        });

        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            if (places == null) return;
            adapter.submit(places);
            boolean hasItems = !places.isEmpty();
            rvCards.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading == null) return;
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);

            if (isLoading) {
                // 載入中先把列表/空畫面都藏起來
                rvCards.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
            } else {
                // 載入結束，依目前資料決定顯示
                boolean hasItems = adapter != null && adapter.getItemCount() > 0;
                rvCards.setVisibility(hasItems ? View.VISIBLE : View.GONE);
                emptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
            }
        });
        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "讀取失敗: " + error);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
        homeViewModel.getEmptyMessage().observe(getViewLifecycleOwner(), message -> {
            if (tvEmpty != null && message != null) {
                tvEmpty.setText(message);
            }
        });
    }
    @Override
    public void onPlaceClick(Place place) {
        if (place != null && place.id != null) {
            showPlaceDetails(place.id);
        }
    }
    private void showPlaceDetails(String placeId) {
        if (getActivity() instanceof AppCompatActivity) {
            PlaceDetailFragment detailFragment = PlaceDetailFragment.newInstance(placeId);
            detailFragment.show(((AppCompatActivity) getActivity()).getSupportFragmentManager(), detailFragment.getTag());
        }
    }
}
