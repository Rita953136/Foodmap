package com.example.fmap.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.Place;
import com.example.fmap.model.FavItem;
import com.example.fmap.model.Swipe;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 首頁卡片清單
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String COLLECTION = "stores_summary";
    private static final int PAGE_SIZE = 10;
    private static final int MAX_ITEMS = 10;

    // UI
    private RecyclerView rvCards;
    private View emptyView;
    private TextView tvEmpty;
    private Button btnGoSearch, btnDevReset;

    private PlacesAdapter adapter;

    // Firestore
    private FirebaseFirestore db;
    private DocumentSnapshot lastDoc = null;
    private boolean isLoading = false;
    private boolean isEnd = false;

    // 收藏儲存
    private FavoritesStore favStore;

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

        favStore = new FavoritesStore(requireContext());

        // find views
        rvCards = v.findViewById(R.id.rvCards);
        emptyView = v.findViewById(R.id.emptyView);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        btnGoSearch = v.findViewById(R.id.btnGoSearch);
        btnDevReset = v.findViewById(R.id.btnDevReset);

        // RecyclerView
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setHasFixedSize(true);
        rvCards.setItemAnimator(null);

        adapter = new PlacesAdapter(position -> {
            Place p = adapter.getItem(position);
            Toast.makeText(requireContext(), p.name, Toast.LENGTH_SHORT).show();
        });
        rvCards.setAdapter(adapter);

        // 綁定左右滑動（LIKE / NOPE）
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new SwipeCallback(adapter, (record, pos) -> {
                    Place p = adapter.getItem(pos);

                    if (record.getAction() == Swipe.Action.LIKE)  {
                        // 右滑加入收藏（upsert）
                        FavItem fav = new FavItem();
                        fav.id = p.id;
                        fav.name = p.name;
                        fav.thumbnailUrl = p.photoUrl;
                        fav.rating = p.rating;
                        fav.tags = p.tags;
                        // 若 Firestore 目前未存經緯度/距離/價位，可留空或 0
                        fav.lat = 0;
                        fav.lng = 0;
                        fav.distanceMeters = null;
                        fav.priceLevel = null;

                        favStore.addOrUpdate(fav);
                        Toast.makeText(requireContext(),
                                "已加入收藏：" + p.name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "略過", Toast.LENGTH_SHORT).show();
                    }

                    Log.d("Swipe", "店家 " + record.getPlaceId() + " → " + record.getAction());
                    if (adapter.getItemCount() == 0) {
                        if (tvEmpty != null) tvEmpty.setText("今日已沒有店家");
                        if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                        if (rvCards != null) rvCards.setVisibility(View.GONE);
                    }
                })
        );
        itemTouchHelper.attachToRecyclerView(rvCards);

        // Firestore
        db = FirebaseFirestore.getInstance();
        loadFirstPage();

    }

    private void loadFirstPage() {
        isEnd = false;
        lastDoc = null;
        queryPage(null);
    }

    private void loadNextPage() {
        if (isEnd || isLoading) return;
        queryPage(lastDoc);
    }

    /** 執行一次分頁查詢 */
    private void queryPage(@Nullable DocumentSnapshot startAfter) {
        isLoading = true;

        Query q = db.collection(COLLECTION)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);
        if (startAfter != null) q = q.startAfter(startAfter);

        q.get()
                .addOnSuccessListener(this::onPageLoaded)
                .addOnFailureListener(this::onLoadFailed);
    }

    private void onPageLoaded(QuerySnapshot snap) {
        isLoading = false;
        List<Place> newList = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            Place p = new Place();
            p.id = d.getId();
            p.name = d.getString("name");
            p.photoUrl = d.getString("photo_url");
            p.introLine = d.getString("intro");

            Object ratingObj = d.get("rating");
            double ratingVal = 0d;
            if (ratingObj instanceof Number) {
                ratingVal = ((Number) ratingObj).doubleValue();
            } else if (ratingObj instanceof String) {
                try { ratingVal = Double.parseDouble(((String) ratingObj).trim()); }
                catch (Exception ignore) { ratingVal = 0d; }
            }
            p.rating = ratingVal;

            List<String> tags = new ArrayList<>();
            Object rawTags = d.get("tags_top3");
            if (rawTags instanceof List) {
                for (Object o : (List<?>) rawTags) {
                    if (o != null) tags.add(String.valueOf(o));
                }
            }
            p.tags = tags;

            newList.add(p);
        }

        // 直接標記已到結尾
        isEnd = true;

        // 合併
        List<Place> merged = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            merged.add(adapter.getItem(i));
        }
        merged.addAll(newList);

        // 保留前 10 筆
        if (merged.size() > MAX_ITEMS) {
            merged = new ArrayList<>(merged.subList(0, MAX_ITEMS));
        }

        // 先算好要不要顯示空狀態，再提交
        final boolean isEmpty = merged.isEmpty();

        adapter.submit(merged);

        // 用剛剛算好的 isEmpty 來切換
        toggleEmpty(isEmpty);

    }

    private void onLoadFailed(Exception e) {
        isLoading = false;
        Log.e(TAG, "讀取失敗", e);
        Toast.makeText(requireContext(),
                "讀取失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        toggleEmpty(adapter.getItemCount() == 0);
    }

    private void toggleEmpty(boolean show) {
        if (emptyView != null) emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvCards != null) rvCards.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show && tvEmpty != null) tvEmpty.setText("目前沒有可顯示的店家");
    }
}
