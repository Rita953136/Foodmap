package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.FavoritesStore;
import com.example.fmap.model.Place;

import java.util.List;

public class FavoriteFragment extends Fragment implements FavoriteAdapter.OnFavoriteClickListener {

    private RecyclerView recyclerView;
    private FavoriteAdapter adapter;
    private FavoritesStore favoritesStore;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        favoritesStore = FavoritesStore.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.rvFavorites);
        emptyView = view.findViewById(R.id.tvEmptyFav);

        setupRecyclerView();
    }
    private void setupRecyclerView() {
        adapter = new FavoriteAdapter(requireContext(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void loadFavorites() {
        if (favoritesStore == null || adapter == null) return;

        List<Place> favoritePlaces = favoritesStore.getAll();
        adapter.submitList(favoritePlaces);

        if (emptyView != null) {
            emptyView.setVisibility(favoritePlaces.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onItemClick(Place place) {
        if (getActivity() instanceof AppCompatActivity && place != null && place.getId() != null) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            // ✅ 帶入兩個參數：placeId + Place（作為查不到資料時的備援）
            PlaceDetailFragment detailFragment = PlaceDetailFragment.newInstance(place.getId(), place);
            detailFragment.show(activity.getSupportFragmentManager(), detailFragment.getTag());
        }
    }

    @Override
    public void onHeartClick(Place place, int position) {
        if (favoritesStore != null && place != null && place.getId() != null) {
            favoritesStore.removeById(place.getId());
            adapter.removeItem(position);

            if (adapter.getItemCount() == 0 && emptyView != null) {
                emptyView.setVisibility(View.VISIBLE);
            }

            Toast.makeText(getContext(), "已取消收藏：" + place.getName(), Toast.LENGTH_SHORT).show();
        }
    }
    /** 進入頁面時讓 Drawer 漢堡變暗＆無法開啟 */
    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerIconEnabled(false); // 🔹 變暗＋鎖定
        }
    }

    /** 離開時恢復 Drawer */
    @Override
    public void onPause() {
        super.onPause();
        loadFavorites();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerIconEnabled(true);  // 🔹 恢復亮亮可點
        }
    }
}
