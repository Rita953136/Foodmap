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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 首頁卡片清單（Firestore 非同步，映射 stores_summary 欄位）
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String COLLECTION = "stores_summary"; // Firestore 集合名
    private static final int PAGE_SIZE = 50;

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

        // Firestore
        db = FirebaseFirestore.getInstance();

        // 捲動到底部自動載下一頁
        rvCards.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (!isLoading && !isEnd && (first + visible) >= (total - 6)) {
                    loadNextPage();
                }
            }
        });

        // 初次載入
        loadFirstPage();

        // 這兩個按鈕依需求實作
        btnGoSearch.setOnClickListener(btn ->
                Toast.makeText(requireContext(), "前往搜尋（TODO）", Toast.LENGTH_SHORT).show());
        btnDevReset.setOnClickListener(btn ->
                Toast.makeText(requireContext(), "重置額度（TODO）", Toast.LENGTH_SHORT).show());
    }

    private void loadFirstPage() {
        isEnd = false;
        lastDoc = null;
        // 如需先清空畫面再載入，可解開下一行
        // adapter.submit(new ArrayList<>());
        queryPage(null);
    }

    private void loadNextPage() {
        if (isEnd || isLoading) return;
        queryPage(lastDoc);
    }

    /** 執行一次分頁查詢（依 rating DESC，可自行更換排序欄位） */
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

    /** 將 Firestore 文件手動映射到 Place，安全解析欄位型別 */
    private void onPageLoaded(QuerySnapshot snap) {
        isLoading = false;

        Log.d(TAG, "載入筆數 = " + snap.size());
        List<Place> newList = new ArrayList<>();

        for (DocumentSnapshot d : snap.getDocuments()) {
            Place p = new Place();
            p.id = d.getId();
            p.name = d.getString("name");
            p.photoUrl = d.getString("photo_url");  // 對應 photo_url
            p.introLine = d.getString("intro");     // 對應 intro

            // rating 可能是 Number 或 String，安全解析
            Object ratingObj = d.get("rating");
            double ratingVal = 0d;
            if (ratingObj instanceof Number) {
                ratingVal = ((Number) ratingObj).doubleValue();
            } else if (ratingObj instanceof String) {
                try { ratingVal = Double.parseDouble(((String) ratingObj).trim()); }
                catch (Exception ignore) { ratingVal = 0d; }
            }
            p.rating = ratingVal;

            // tags_top3 解析為 List<String>
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

        // 分頁游標
        if (snap.isEmpty()) {
            isEnd = true;
        } else {
            lastDoc = snap.getDocuments().get(snap.size() - 1);
        }

        // 合併舊資料 + 新資料
        List<Place> merged = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            merged.add(adapter.getItem(i));
        }
        merged.addAll(newList);
        adapter.submit(merged);

        toggleEmpty(adapter.getItemCount() == 0);
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
