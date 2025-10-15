package com.example.fmap.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.FavItem;
import com.example.fmap.model.Place;
import com.example.fmap.model.Swipe;

import java.util.List;

/**
 * 首頁卡片清單
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // UI
    private RecyclerView rvCards;
    private View emptyView;
    private TextView tvEmpty;
    private PlacesAdapter adapter;

    // ViewModel
    private HomeViewModel homeViewModel;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Initialize ViewModel
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // find views
        rvCards = v.findViewById(R.id.rvCards);
        emptyView = v.findViewById(R.id.emptyView);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        setupRecyclerView();
        observeViewModel();

        // Load data if not already loaded
        if (homeViewModel.getPlaces().getValue() == null) {
            homeViewModel.loadPlaces();
        }
    }

    private void setupRecyclerView() {
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setHasFixedSize(true);
        rvCards.setItemAnimator(null);

        adapter = new PlacesAdapter(position -> {
            Place p = adapter.getItem(position);
            Toast.makeText(requireContext(), p.name, Toast.LENGTH_SHORT).show();
        });
        rvCards.setAdapter(adapter);

        // Attach swipe-to-like/dislike functionality
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new SwipeCallback(adapter, this::handleSwipeAction)
        );
        itemTouchHelper.attachToRecyclerView(rvCards);
    }

    private void observeViewModel() {
        // Observe places data
        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            adapter.submit(places);
            toggleEmpty(places.isEmpty());
        });

        // Observe loading status
        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            // You can show/hide a progress bar here if you have one
        });

        // Observe errors
        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "讀取失敗: " + error);
                Toast.makeText(requireContext(), "讀取失敗: " + error, Toast.LENGTH_LONG).show();
                toggleEmpty(adapter.getItemCount() == 0);
            }
        });

        // Observe empty state message
        homeViewModel.getEmptyMessage().observe(getViewLifecycleOwner(), message -> {
            if (tvEmpty != null) {
                tvEmpty.setText(message);
            }
        });
    }
    private void handleSwipeAction(Swipe swipe, int pos) {
        Place p = adapter.getItem(pos);
        if (swipe.getAction() == Swipe.Action.LIKE) {
            homeViewModel.addToFavorites(p);
            Toast.makeText(requireContext(), "已加入收藏：" + p.name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "略過", Toast.LENGTH_SHORT).show();
        }

        Log.d("Swipe", "店家 " + swipe.getPlaceId() + " → " + swipe.getAction());

        if (adapter.getItemCount() == 0) {
            homeViewModel.setNoMorePlacesMessage();
        }
    }


    private void toggleEmpty(boolean show) {
        if (emptyView != null) emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvCards != null) rvCards.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}
