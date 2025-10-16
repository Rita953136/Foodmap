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
import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.Place;

import java.util.List;

/**
 * 負責顯示「我的收藏」頁面的 Fragment
 */
public class FavoriteFragment extends Fragment implements FavoriteAdapter.OnFavoriteClickListener {

    private RecyclerView recyclerView;
    private FavoriteAdapter adapter;
    private FavoritesStore favoritesStore;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 載入 fragment_favorite.xml 佈局
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ✅ 改成透過 getInstance() 取得單例
        favoritesStore = FavoritesStore.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.rvFavorites);
        emptyView = view.findViewById(R.id.tvEmptyFav);

        setupRecyclerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到這個頁面時，都重新載入一次收藏列表
        loadFavorites();
    }

    /**
     * 設定 RecyclerView 和 Adapter
     */
    private void setupRecyclerView() {
        adapter = new FavoriteAdapter(requireContext(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    /**
     * 從 FavoritesStore 載入資料並更新 UI
     */
    private void loadFavorites() {
        if (favoritesStore == null || adapter == null) return;

        List<Place> favoritePlaces = favoritesStore.getAll();
        adapter.submitList(favoritePlaces);

        if (emptyView != null) {
            emptyView.setVisibility(favoritePlaces.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // --- 實作 FavoriteAdapter.OnFavoriteClickListener ---

    @Override
    public void onItemClick(Place place) {
        if (getActivity() instanceof AppCompatActivity && place != null && place.id != null) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            PlaceDetailFragment detailFragment = PlaceDetailFragment.newInstance(place.id);
            detailFragment.show(activity.getSupportFragmentManager(), detailFragment.getTag());
        }
    }

    @Override
    public void onHeartClick(Place place, int position) {
        if (favoritesStore != null && place != null && place.id != null) {
            favoritesStore.remove(place.id);
            adapter.removeItem(position);

            if (adapter.getItemCount() == 0 && emptyView != null) {
                emptyView.setVisibility(View.VISIBLE);
            }

            Toast.makeText(getContext(), "已取消收藏：" + place.name, Toast.LENGTH_SHORT).show();
        }
    }
}
