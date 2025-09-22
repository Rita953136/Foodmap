package com.example.fmap.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.FavItem;

import java.util.ArrayList;
import java.util.List;
import com.example.fmap.model.FakeData;

public class FavoriteFragment extends Fragment {

    private FavoritesStore store;
    private FavoriteAdapter adapter;
    private TextView empty;
    private final ActivityResultLauncher<Intent> detailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    adapter.submit(FakeData.fakeFavItems(10)); // 這裡設定 10 筆假資料
                    updateEmpty();

                }
            });
    private boolean pendingReload = false;
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        store = new FavoritesStore(requireContext());

        RecyclerView rv = v.findViewById(R.id.rvFavorites);
        empty = v.findViewById(R.id.tvEmptyFav);

        adapter = new FavoriteAdapter(
                // 右側愛心：取消收藏
                (pos, item) -> {
                    store.remove(item.id);
                    adapter.removeAt(pos);
                    Toast.makeText(requireContext(), "已取消收藏：" + item.name, Toast.LENGTH_SHORT).show();
                    updateEmpty();
                },
                // 詳細頁
                (pos, item) -> {
                    Intent it = new Intent(requireContext(), PlaceDetailActivity.class);
                    it.putExtra("id", item.id);
                    it.putExtra("name", item.name);
                    it.putExtra("thumb", item.thumbnailUrl);
                    detailLauncher.launch(it);
                    if (item.rating != null)   it.putExtra("rating", item.rating);
                    if (item.distanceMeters != null) it.putExtra("distance", item.distanceMeters);
                    if (item.priceLevel != null) it.putExtra("price", item.priceLevel);
                    it.putStringArrayListExtra("tags",
                            item.tags == null ? new ArrayList<>() : new ArrayList<>(item.tags));
                    startActivity(it);
                }
        );

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // 使用假資料（共用 FakeData）
        adapter.submit(FakeData.fakeFavItems(10));
        updateEmpty();

        }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            adapter.submit(store.getAll());
            updateEmpty();
        }
    }

    private void updateEmpty() {
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
