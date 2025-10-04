package com.example.fmap.ui.home;

import android.os.Bundle;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.fmap.R;
import com.example.fmap.data.MockPlacesRepository;
import com.example.fmap.data.RepositoryProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;        // 抽屜只當「標籤選單容器」
    private NavigationView navView;           // menu/nav_menu：可勾選的標籤
    private BottomNavigationView bottomNav;   // menu/bottom_menu：home/map/favorite/user

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 資料來源（沿用 mock）
        RepositoryProvider.init(new MockPlacesRepository(getAssets()));
        setContentView(R.layout.activity_main);

        // Toolbar + Drawer（僅提供開關抽屜）
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView      = findViewById(R.id.nav_view); // 不綁 listener，HomeFragment 會處理

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.open_nav, R.string.close_nav);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // BottomNavigation：切換四個分頁
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            // 切頁前清空 back stack，避免層層疊
            FragmentManager fm = getSupportFragmentManager();
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            int id = item.getItemId();
            if (id == R.id.home) {
                switchTab("home", new HomeFragment());
            } else if (id == R.id.map) {
                switchTab("map", new MapFragment());
            } else if (id == R.id.favorite) {
                switchTab("fav", new FavoriteFragment());
            } else if (id == R.id.user) {
                switchTab("user", new UserFragment());
            }

            // 若抽屜開著，順手關閉
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });

        // 預設進入首頁
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.home); // 對應 @menu/bottom_menu 裡的 item id
        }
    }

    /** 切換底部分頁：若 fragment 已存在就 show，否則 add */
    private Fragment switchTab(String tag, Fragment target) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        // 隱藏所有現有 fragment
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            ft.hide(f);
        }

        // 顯示或新增目標 fragment
        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        Fragment toShow = (existing != null) ? existing : target;
        if (existing == null) ft.add(R.id.fragment_container, toShow, tag);
        else ft.show(toShow);

        ft.commit();
        return toShow;
    }

    /** 讓其他頁面可以程式化切換底部分頁 */
    public void selectBottomTab(int menuId) {
        if (bottomNav != null) bottomNav.setSelectedItemId(menuId);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }
}
