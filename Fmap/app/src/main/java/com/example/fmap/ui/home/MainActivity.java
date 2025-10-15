package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.fmap.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;        // 側邊抽屜
    private ChipGroup chipGroup;              // 標籤過濾
    private BottomNavigationView bottomNav;   // 底部導覽列
    private HomeViewModel homeVM;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 設定 Toolbar 與抽屜開關
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.open_nav, R.string.close_nav);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 取得 ViewModel
        homeVM = new ViewModelProvider(this).get(HomeViewModel.class);

        // 設定抽屜內的標籤點擊事件
        View drawerContent = findViewById(R.id.drawer_content);
        chipGroup = drawerContent.findViewById(R.id.chip_group_tags);

        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                List<String> selected = new ArrayList<>();
                if (checkedIds != null) {
                    for (Integer id : checkedIds) {
                        Chip c = group.findViewById(id);
                        if (c != null) selected.add(c.getText().toString().trim());
                    }
                }
                // 套用標籤篩選
                homeVM.applyTagFilter(selected);
            });
            // 預設載入全部
            homeVM.applyTagFilter(java.util.Collections.emptyList());
        }

        // 設定底部導覽列點擊事件
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            // 避免重複點擊
            if (bottomNav.getSelectedItemId() == item.getItemId()) return true;

            // 清空返回堆疊
            FragmentManager fm = getSupportFragmentManager();
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            int id = item.getItemId();
            if (id == R.id.home) {
                switchTab("home", new HomeFragment());
            } else if (id == R.id.map) {
                switchTab("map", new MapFragment());
            } else if (id == R.id.favorite) {
                switchTab("fav", new FavoriteFragment());
            }

            // 若抽屜開啟則關閉
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });

        // 預設顯示首頁
        if (savedInstanceState == null) {
            switchTab("home", new HomeFragment());
            bottomNav.setSelectedItemId(R.id.home);
        }

        // 自訂返回按鈕行為
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    // 非首頁則返回首頁，否則退出
                    if (bottomNav.getSelectedItemId() != R.id.home) {
                        bottomNav.setSelectedItemId(R.id.home);
                    } else {
                        setEnabled(false);
                        MainActivity.super.onBackPressed();
                    }
                }
            }
        });
    }

    /** 依 Tag 切換 Fragment */
    private Fragment switchTab(String tag, Fragment target) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction()
                .setReorderingAllowed(true);

        // 隱藏所有 Fragment
        for (Fragment f : fm.getFragments()) {
            ft.hide(f);
        }

        // 顯示或新增目標 Fragment
        Fragment existing = fm.findFragmentByTag(tag);
        Fragment toShow = (existing != null) ? existing : target;
        if (existing == null) {
            ft.add(R.id.fragment_container, toShow, tag);
        } else {
            ft.show(toShow);
        }

        ft.commit();
        return toShow;
    }
}
