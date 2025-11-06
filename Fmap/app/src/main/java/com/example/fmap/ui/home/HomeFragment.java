package com.example.fmap.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment implements PlacesAdapter.OnPlaceClickListener {
    private static final String TAG = "HomeFragment";

    private RecyclerView rvCards;
    private View emptyView;
    private TextView tvEmpty;
    private ProgressBar loadingIndicator;
    private PlacesAdapter adapter;
    private HomeViewModel homeViewModel;
    private ChipGroup chipGroupTags;

    // 自訂搜尋列（與 Map 相同樣式）
    private EditText etHomeSearch;
    private ImageButton btnHomeSearch, btnHomeClear;

    // Drawer
    private DrawerLayout drawerLayout;
    // 如果你的 Drawer 有多個，且只想在特定抽屜關閉時觸發，填上該 drawer view 的 id；否則設為 0
    private static final int TARGET_DRAWER_VIEW_ID = 0; // 例如 R.id.nav_view，沒有就維持 0

    private final DrawerLayout.DrawerListener drawerListener = new DrawerLayout.SimpleDrawerListener() {
        @Override
        public void onDrawerClosed(@NonNull View drawerView) {
            if (TARGET_DRAWER_VIEW_ID != 0 && drawerView.getId() != TARGET_DRAWER_VIEW_ID) return;
            List<String> selected = collectSelectedCategoriesFromChips();
            if (!sameAsViewModel(selected)) {
                Log.d(TAG, "DrawerClosed -> 套用類別: " + selected);
                homeViewModel.applyTagFilter(selected);
            } else {
                Log.d(TAG, "DrawerClosed -> 類別未變更，略過 reload");
            }
        }
    };

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
        // 多標籤預設 ANY（店家只要命中其中一個）
        homeViewModel.setTagMatchMode(HomeViewModel.TagMatchMode.ANY);

        initViews(v);
        ensureChipGroupMultiSelect();
        setupRecyclerView();
        bindSearchBar();          // ★ 改用自訂搜尋列
        // 若你想「勾選當下就更新」可打開下一行；目前需求是關閉側欄才更新所以不啟用
        // bindChipImmediateUpdate();
        observeViewModel();

        // 取得 DrawerLayout 並註冊監聽（假設 Activity 的 DrawerLayout id 為 drawer_layout）
        drawerLayout = requireActivity().findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(drawerListener);
        } else {
            Log.w(TAG, "找不到 DrawerLayout (R.id.drawer_layout)，無法在關閉時套用類別。");
        }

        if (homeViewModel.getPlaces().getValue() == null) {
            homeViewModel.loadPlaces();
        }
    }

    private void initViews(@NonNull View view) {
        rvCards = view.findViewById(R.id.rvCards);
        emptyView = view.findViewById(R.id.emptyView);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        chipGroupTags = view.findViewById(R.id.chip_group_tags);

        // 自訂搜尋列（請確保 fragment_home.xml 已有這三個 id）
        etHomeSearch = view.findViewById(R.id.et_home_search);
        btnHomeSearch = view.findViewById(R.id.btn_home_search);
        btnHomeClear = view.findViewById(R.id.btn_home_clear);
    }

    /** 保險：即便 XML 設了，仍強制一次多選 */
    private void ensureChipGroupMultiSelect() {
        if (chipGroupTags == null) return;
        chipGroupTags.setSingleSelection(false);
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
                    homeViewModel.handleSwipeAction(swipeAction, swipedPlace);

                    String name = swipedPlace.getName();
                    if (swipeAction == Swipe.Action.LIKE) {
                        Toast.makeText(getContext(), "已收藏：" + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "已將 " + name + " 加入不喜歡列表", Toast.LENGTH_SHORT).show();
                    }
                })
        );
        itemTouchHelper.attachToRecyclerView(rvCards);
    }

    /** 與 Map 搜尋列相同行為：鍵盤送出、搜尋按鈕、清除按鈕 */
    private void bindSearchBar() {
        if (etHomeSearch == null) return;

        // 鍵盤「搜尋」動作
        etHomeSearch.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String q = etHomeSearch.getText() != null ? etHomeSearch.getText().toString() : "";
                homeViewModel.applySearchQuery(q);
                etHomeSearch.clearFocus();
                return true;
            }
            return false;
        });

        // 右側搜尋按鈕
        if (btnHomeSearch != null) {
            btnHomeSearch.setOnClickListener(v -> {
                String q = etHomeSearch.getText() != null ? etHomeSearch.getText().toString() : "";
                homeViewModel.applySearchQuery(q);
                etHomeSearch.clearFocus();
            });
        }

        // 清除按鈕
        if (btnHomeClear != null) {
            btnHomeClear.setOnClickListener(v -> {
                etHomeSearch.setText("");
                homeViewModel.applySearchQuery("");
                etHomeSearch.clearFocus();
            });
        }
    }

    /**
     * （可選）若你同時也想在「勾選 chip 當下」就更新，打開 onViewCreated 裡的呼叫並保留此方法
     * 目前依你的需求（關閉側欄才刷新），預設不啟用。
     */
    private void bindChipImmediateUpdate() {
        if (chipGroupTags == null) return;
        chipGroupTags.setOnCheckedStateChangeListener((group, checkedIds) -> {
            List<String> selected = collectSelectedCategoriesFromChips();
            Log.d(TAG, "UI -> 即時選取類別: " + selected);
            homeViewModel.applyTagFilter(selected);
        });
    }

    /** LiveData observers：以 places 決定可見度；isLoading 只控轉圈圈 */
    private void observeViewModel() {
        homeViewModel.getSelectedTags().observe(getViewLifecycleOwner(), tags ->
                Log.d(TAG, "VM -> 標籤(類別)已更新: " + tags)
        );

        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            if (places == null) return;

            Log.d(TAG, "UI 接到 places 筆數=" + places.size());
            adapter.submit(places);
            // 保險：即使 submit() 未用 DiffUtil，也確保刷新
            adapter.notifyDataSetChanged();

            boolean hasItems = !places.isEmpty();
            rvCards.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyView.setVisibility(hasItems ? View.GONE : View.VISIBLE);

            rvCards.bringToFront();
            rvCards.invalidate();
        });

        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading == null) return;
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
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

    /** 收集目前 ChipGroup 被勾選的文字（使用顯示字串本身） */
    private List<String> collectSelectedCategoriesFromChips() {
        List<String> selected = new ArrayList<>();
        if (chipGroupTags == null) return selected;
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip) {
                Chip c = (Chip) child;
                if (c.isChecked()) selected.add(String.valueOf(c.getText()));
            }
        }
        return selected;
    }

    /** 比對 UI 勾選與 ViewModel 既有選擇是否相同（忽略順序與大小寫/全半形） */
    private boolean sameAsViewModel(List<String> uiSelected) {
        List<String> vm = homeViewModel.getSelectedTags().getValue();
        if (vm == null) vm = new ArrayList<>();
        Set<String> a = new HashSet<>();
        for (String s : uiSelected) a.add(norm(s));
        Set<String> b = new HashSet<>();
        for (String s : vm) b.add(norm(s));
        return a.equals(b);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String x = s.replace("\ufeff", "")
                .replace("\u200b", "").replace("\u200c", "").replace("\u200d", "")
                .trim();
        x = Normalizer.normalize(x, Normalizer.Form.NFKC);
        return x.toLowerCase(Locale.ROOT);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (drawerLayout != null) {
            drawerLayout.removeDrawerListener(drawerListener);
        }
    }

    @Override
    public void onPlaceClick(Place place) {
        PlaceDetailFragment f = PlaceDetailFragment.newInstance(
                place.getId(),
                place
        );
        f.show(getParentFragmentManager(), "place_detail");
    }
}
