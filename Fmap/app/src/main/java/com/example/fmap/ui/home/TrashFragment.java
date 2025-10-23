package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;

import java.util.List;

/** 垃圾桶頁（不喜歡清單），純本機版（容錯 ProgressBar id） */
public class TrashFragment extends Fragment implements TrashCardAdapter.OnTrashActionListener {

    private RecyclerView rvTrash;
    private ProgressBar progress; // 可能為 null
    private TextView tvEmpty;

    private TrashCardAdapter adapter;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        rvTrash  = v.findViewById(R.id.rvTrash);
        tvEmpty  = v.findViewById(R.id.tvEmptyTrash);
        progress = findProgressBar(v); // ← 容錯尋找

        adapter = new TrashCardAdapter(requireContext(), this);
        rvTrash.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTrash.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        observeViewModel();
        viewModel.loadDislikedPlacesFromPrefs(); // 載入一次
    }

    /** 依序嘗試常見 id，找不到回傳 null */
    /** 依序嘗試常見 id（不直接引用 R.id，避免編譯期紅字） */
    private @Nullable ProgressBar findProgressBar(@NonNull View root) {
        // 1) 先試專案常用的 loading_indicator（若 layout 有的話）
        ProgressBar pb = root.findViewById(R.id.loading_indicator);
        if (pb != null) return pb;

        // 2) 以名稱動態尋找 progressTrash
        int id = getResources().getIdentifier("progressTrash", "id", requireContext().getPackageName());
        if (id != 0) {
            pb = root.findViewById(id);
            if (pb != null) return pb;
        }

        // 3) 以名稱動態尋找 progressBar
        id = getResources().getIdentifier("progressBar", "id", requireContext().getPackageName());
        if (id != 0) {
            pb = root.findViewById(id);
            if (pb != null) return pb;
        }

        // 找不到就不顯示進度條
        return null;
    }


    private void observeViewModel() {
        viewModel.getIsLoadingTrash().observe(getViewLifecycleOwner(), loading -> {
            boolean show = loading != null && loading;
            if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                rvTrash.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.GONE);
            }
        });

        viewModel.getTrashError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getDislikedPlaces().observe(getViewLifecycleOwner(), this::renderList);
    }

    private void renderList(List<Place> list) {
        adapter.submit(list);
        boolean has = list != null && !list.isEmpty();
        rvTrash.setVisibility(has ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRestore(@NonNull Place place, int position) {
        if (place.getId() == null) return;
        viewModel.removeFromDislikes(place.getId());
        adapter.removeAt(position);
        if (adapter.getItemCount() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
        }
        Toast.makeText(getContext(),
                "已復原：「" + (place.getName() != null ? place.getName() : "") + "」",
                Toast.LENGTH_SHORT).show();
    }
}
