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

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class HomeFragment extends Fragment implements PlacesAdapter.OnPlaceClickListener {
    private static final String TAG = "HomeFragment";

    private androidx.recyclerview.widget.RecyclerView rvCards;
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

        // 純本機版不需要資料來源設定，直接使用
        homeViewModel.setTagMatchMode(HomeViewModel.TagMatchMode.ANY);

        initializeViews(v);
        setupRecyclerView();
        observeViewModel();
        setupTagMultiSelect();

        if (homeViewModel.getPlaces().getValue() == null) {
            homeViewModel.loadPlaces();
        }
    }

    private void setupTagMultiSelect() {
        if (chipGroupTags == null) return;

        chipGroupTags.setOnCheckedStateChangeListener((group, checkedIds) -> {
            java.util.List<String> selected = new java.util.ArrayList<>();
            for (int id : checkedIds) {
                Chip chip = group.findViewById(id);
                if (chip != null && chip.isChecked()) {
                    selected.add(chip.getText().toString());
                }
            }
            homeViewModel.applyTagFilter(selected);
        });
    }

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

                    String name = swipedPlace.getName(); // ← 改用 getter
                    if (swipeAction == Swipe.Action.LIKE) {
                        Toast.makeText(getContext(), "已收藏：" + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "已將 " + name + " 加入不喜歡列表", Toast.LENGTH_SHORT).show();
                    }
                })
        );
        itemTouchHelper.attachToRecyclerView(rvCards);
    }

    private void observeViewModel() {
        homeViewModel.getSelectedTags().observe(getViewLifecycleOwner(), tags ->
                Log.d(TAG, "標籤已更新，當前篩選條件: " + tags)
        );

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
                rvCards.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
            } else {
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
        PlaceDetailFragment f = PlaceDetailFragment.newInstance(
                place.getId(),   // placeId
                place            // 備援的 Place 物件
        );
        f.show(getParentFragmentManager(), "place_detail");
    }


    private void showPlaceDetails(String placeId) {
        if (getActivity() instanceof AppCompatActivity) {
            PlaceDetailFragment detailFragment = PlaceDetailFragment.newInstance(placeId);
            detailFragment.show(((AppCompatActivity) getActivity()).getSupportFragmentManager(),
                    detailFragment.getTag());
        }
    }
}
