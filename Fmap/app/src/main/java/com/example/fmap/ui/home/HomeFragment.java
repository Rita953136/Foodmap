package com.example.fmap.ui.home;

import android.os.Bundle;
import android.util.Log;import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar; // ✨ 1. 新增 ProgressBar
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

/**
 * App 的主頁，負責顯示推薦卡片和處理使用者互動。
 * 這個版本與最新的 HomeViewModel 完全同步。
 */
public class HomeFragment extends Fragment implements PlacesAdapter.OnPlaceClickListener {

    private static final String TAG = "HomeFragment";

    // UI 元件
    private RecyclerView rvCards;
    private View emptyView;
    private TextView tvEmpty;
    private ProgressBar loadingIndicator; // ✨ 2. 新增讀取圈的變數
    private PlacesAdapter adapter;

    // ViewModel
    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // 如果你的 Activity 有設定 Toolbar 的方法，可以在這裡呼叫
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setHomeToolbar();
        }

        // 初始化 ViewModel
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        // 初始化 View
        initializeViews(v);

        // 設定 RecyclerView
        setupRecyclerView();

        // 觀察 ViewModel 的 LiveData
        observeViewModel();

        // 如果 ViewModel 中沒有資料，才觸發初次載入
        if (homeViewModel.getPlaces().getValue() == null) {
            homeViewModel.loadPlaces();
        }
    }

    /**
     * 將所有 findViewById 的操作集中管理
     */
    private void initializeViews(@NonNull View view) {
        rvCards = view.findViewById(R.id.rvCards);
        emptyView = view.findViewById(R.id.emptyView);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        // ✨ 3. 假設你的 fragment_home.xml 中有一個 ID 為 loading_indicator 的 ProgressBar
        // 如果沒有，你需要去 XML 中加入它。
        loadingIndicator = view.findViewById(R.id.loading_indicator);
    }

    /**
     * 設定 RecyclerView、Adapter 和滑動輔助
     */
    private void setupRecyclerView() {
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setHasFixedSize(true);
        // 建立 Adapter 時，將 this (Fragment 本身) 作為監聽器傳入
        adapter = new PlacesAdapter(requireContext(), this);
        rvCards.setAdapter(adapter);

        // 設定滑動邏輯
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new SwipeCallback(adapter, (swipeAction, pos) -> {
                    Place swipedPlace = adapter.getItem(pos);
                    homeViewModel.handleSwipeAction(swipeAction, swipedPlace);

                    if (swipeAction == Swipe.Action.LIKE && swipedPlace != null) {
                        Toast.makeText(getContext(), "已收藏：" + swipedPlace.name, Toast.LENGTH_SHORT).show();
                    }
                })
        );
        itemTouchHelper.attachToRecyclerView(rvCards);
    }

    /**
     * 觀察 ViewModel 中的所有 LiveData
     */
    private void observeViewModel() {
        // 觀察店家列表資料
        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            if (places != null) {
                adapter.submit(places);
                // 只有在不在載入狀態時，才根據列表是否為空來顯示空 View
                if (!Boolean.TRUE.equals(homeViewModel.getIsLoading().getValue())) {
                    emptyView.setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);
                    rvCards.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });

        // ✨ 4. 觀察載入狀態 (isLoading)
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                // 正在載入時，應隱藏卡片和空狀態提示
                if (isLoading) {
                    rvCards.setVisibility(View.GONE);
                    emptyView.setVisibility(View.GONE);
                }
            }
        });

        // 觀察錯誤訊息
        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "讀取失敗: " + error);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        // 觀察空狀態的提示文字
        homeViewModel.getEmptyMessage().observe(getViewLifecycleOwner(), message -> {
            if (tvEmpty != null && message != null) {
                tvEmpty.setText(message);
            }
        });
    }

    /**
     * 實現 Adapter 的點擊介面方法，將點擊操作轉發給 ViewModel
     * @param place 被點擊的店家
     */
    @Override
    public void onPlaceClick(Place place) {
        if (place != null && place.id != null) {
            showPlaceDetails(place.id);
        }
    }

    /**
     * 將顯示店家詳情面板的邏輯抽取成獨立方法
     * @param placeId 要顯示的店家 ID
     */
    private void showPlaceDetails(String placeId) {
        if (getActivity() instanceof AppCompatActivity) {
            PlaceDetailFragment detailFragment = PlaceDetailFragment.newInstance(placeId);
            detailFragment.show(((AppCompatActivity) getActivity()).getSupportFragmentManager(), detailFragment.getTag());
        }
    }
}
