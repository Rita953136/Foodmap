package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class TrashFragment extends Fragment {

    private HomeViewModel homeVM;
    private RecyclerView rvTrash;
    private TrashCardAdapter TrashCardAdapter;
    private TextView tvEmptyTrash;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化 ViewModel 和 View
        homeVM = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        rvTrash = view.findViewById(R.id.rvTrash);
        tvEmptyTrash = view.findViewById(R.id.tvEmptyTrash);

        setupRecyclerView();
        observeViewModel();
        homeVM.loadDislikedPlacesFromPrefs();
    }

    private void setupRecyclerView() {
        TrashCardAdapter = new TrashCardAdapter(new TrashCardAdapter.Listener() {
            @Override
            public void onRestore(@NonNull Place p) {
                homeVM.removeFromDislikes(p.id);
                Toast.makeText(getContext(), "已還原 " + (p.name != null ? p.name : ""), Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onDelete(@NonNull Place p) {
                Toast.makeText(getContext(), "已刪除 " + (p.name != null ? p.name : ""), Toast.LENGTH_SHORT).show();
            }
        });

        rvTrash.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTrash.setAdapter(TrashCardAdapter);
    }


    private void observeViewModel() {
        homeVM.getDislikedPlaces().observe(getViewLifecycleOwner(), places -> {
            if (places != null && !places.isEmpty()) {
                TrashCardAdapter.submitList(places);
                rvTrash.setVisibility(View.VISIBLE);
                tvEmptyTrash.setVisibility(View.GONE);
            } else {
                rvTrash.setVisibility(View.GONE);
                tvEmptyTrash.setVisibility(View.VISIBLE);
            }
        });

        homeVM.getTrashError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
