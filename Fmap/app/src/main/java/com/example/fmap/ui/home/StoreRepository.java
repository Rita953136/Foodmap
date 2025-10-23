package com.example.fmap.ui.home;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.fmap.data.StoresRepository;
import com.example.fmap.data.local.StoreEntity;

import java.util.List;

/** 適配層：讓 HomeViewModel 使用的型別保持不變 */
public class StoreRepository {
    private final StoresRepository impl;

    public StoreRepository(Application app) { impl = new StoresRepository(app); }

    public void initFromAssets(Context ctx) { impl.initFromAssets(ctx); }

    public androidx.lifecycle.LiveData<Boolean> getDbReady() { return impl.getDbReady(); }

    public LiveData<List<StoreEntity>> searchAdvanced(String keyword, List<String> categories,
                                                      String dishLike, String priceEq) {
        return impl.searchAdvanced(keyword, categories, dishLike, priceEq);
    }

    public LiveData<List<StoreEntity>> getByIds(List<String> ids) { return impl.getByIds(ids); }
}
