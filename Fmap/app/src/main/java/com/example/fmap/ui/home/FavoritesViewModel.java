package com.example.fmap.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fmap.model.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理收藏資料的 ViewModel
 */
public class FavoritesViewModel extends AndroidViewModel {

    private final FavoritesStore store;
    private final MutableLiveData<List<Place>> favorites = new MutableLiveData<>(new ArrayList<>());

    public FavoritesViewModel(@NonNull Application app) {
        super(app);
        // ✅ 改為使用單例取得 FavoritesStore
        store = FavoritesStore.getInstance(app.getApplicationContext());
    }

    /**
     * 提供收藏列表的 LiveData 給 UI 觀察
     */
    public LiveData<List<Place>> getFavorites() {
        return favorites;
    }

    /**
     * 重新載入收藏資料
     */
    public void load() {
        favorites.setValue(store.getAll());
    }

    /**
     * 依照 ID 移除收藏，並更新列表
     */
    public void removeById(String id) {
        store.remove(id);
        load(); // 重新載入以更新 UI
    }
}
