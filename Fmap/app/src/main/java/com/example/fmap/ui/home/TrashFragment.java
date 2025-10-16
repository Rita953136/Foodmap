package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.google.android.material.snackbar.Snackbar;

/**
 * 顯示「垃圾桶」列表頁，現在由 ViewModel 提供資料
 */
public class TrashFragment extends Fragment {

    private HomeViewModel homeVM;
    private RecyclerView rv;
    private View tvEmpty; // 用來顯示「垃圾桶是空的」的文字視圖
    private TrashCardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 設定 Toolbar 和返回按鈕
        setupToolbar();
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // 初始化 ViewModel
        homeVM = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        // 綁定 UI 元件
        rv = v.findViewById(R.id.rvTrash); // <<< 修正 ID
        tvEmpty = v.findViewById(R.id.tvEmpty);

        // 設定 RecyclerView
        setupRecyclerView();

        // 觀察 ViewModel 的資料變化
        observeViewModel();

        // 首次進入時，命令 ViewModel 載入資料
        homeVM.loadDislikedPlaces();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 恢復 MainActivity 的抽屜功能
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerEnabled(true);
        }
    }

    private void setupToolbar() {
        // 確保 Activity 和 ActionBar 存在
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            // 禁用 MainActivity 的側邊抽屜
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).setDrawerEnabled(false);
            }
        }
    }

    private void setupRecyclerView() {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 初始化 Adapter，並傳入事件監聽器
        adapter = new TrashCardAdapter(new TrashCardAdapter.Listener() {
            @Override
            public void onRestore(@NonNull Place p) {
                // 命令 ViewModel 處理「還原」邏輯
                homeVM.removeFromDislikes(p.id);
                Snackbar.make(requireView(), "已還原「" + p.name + "」", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(@NonNull Place p) {
                // 如果需要永久刪除，可以新增一個 ViewModel 方法
                // 這裡暫時也用還原代替
                homeVM.removeFromDislikes(p.id);
                Snackbar.make(requireView(), "已從垃圾桶移除", Snackbar.LENGTH_SHORT).show();
            }
        });
        rv.setAdapter(adapter);
    }

    private void observeViewModel() {
        // 觀察「不喜歡的店家列表」
        homeVM.getDislikedPlaces().observe(getViewLifecycleOwner(), places -> {
            // 當 LiveData 更新時，提交列表給 Adapter
            adapter.submitList(places);
            // 根據列表是否為空，決定是否顯示「空狀態」的文字
            boolean isEmpty = (places == null || places.isEmpty());
            tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        // 觀察「讀取狀態」
        homeVM.getIsLoadingTrash().observe(getViewLifecycleOwner(), isLoading -> {
            // 可以在此處顯示或隱藏讀取動畫 (ProgressBar)
        });

        // 觀察「錯誤訊息」
        homeVM.getTrashError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
