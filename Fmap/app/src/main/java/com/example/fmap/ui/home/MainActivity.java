package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private BottomNavigationView bottomNav;
    private HomeViewModel homeVM;
    private Toolbar toolbar;
    private ActionBarDrawerToggle toggle;

    private TextView tvTitle;
    private TextView tvDate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        tvTitle = findViewById(R.id.tvTitle);
        tvDate = findViewById(R.id.tvDate);

        drawerLayout = findViewById(R.id.drawer_layout);
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.open_nav, R.string.close_nav);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        homeVM = new ViewModelProvider(this).get(HomeViewModel.class);

        setupDrawerChips();
        setupBottomNav();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.home);
        }

        setupBackPressLogic();

        // 監聽 Fragment 變化並自動更新 UI
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            boolean isHome = currentFragment instanceof HomeFragment;
            setDrawerEnabled(isHome);

            if (isHome) {
                setHomeToolbar();
            } else if (currentFragment instanceof TrashFragment) {
                if (getSupportActionBar() != null) {
                    if (tvTitle != null && tvDate != null) {
                        tvTitle.setVisibility(View.GONE);
                        tvDate.setVisibility(View.GONE);
                    }
                    // 顯示預設標題
                    getSupportActionBar().setDisplayShowTitleEnabled(true);
                }
            }
        });
    }

    public void setHomeToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        if (tvTitle != null && tvDate != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvDate.setVisibility(View.VISIBLE);
            tvTitle.setText(R.string.title_home);
            SimpleDateFormat sdf = new SimpleDateFormat("M月d日", Locale.TAIWAN);
            tvDate.setText(sdf.format(new Date()));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_trash) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof TrashFragment) {
                getOnBackPressedDispatcher().onBackPressed();
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TrashFragment())
                        .addToBackStack(null) // 加入返回堆疊
                        .commit();
            }
            return true;
        } else if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home_toolbar, menu);
        return true;
    }

    private void setupDrawerChips() {
        ChipGroup chipGroup = findViewById(R.id.chip_group_tags);
        if (chipGroup == null) return;
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            List<String> selected = new ArrayList<>();
            for (Integer id : checkedIds) {
                Chip c = group.findViewById(id);
                if (c != null) {
                    selected.add(c.getText().toString().trim());
                }
            }
            homeVM.applyTagFilter(selected);
        });
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (bottomNav.getSelectedItemId() == item.getItemId()) {
                return false;
            }
            Fragment targetFragment = null;
            int id = item.getItemId();
            if (id == R.id.home) {
                targetFragment = new HomeFragment();
            } else if (id == R.id.map) {
                targetFragment = new MapFragment();
            } else if (id == R.id.favorite) {
                targetFragment = new FavoriteFragment();
            }
            if (targetFragment != null) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, targetFragment)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .commit();
            }
            return true;
        });
    }

    private void setupBackPressLogic() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else if (bottomNav.getSelectedItemId() != R.id.home) {
                    bottomNav.setSelectedItemId(R.id.home);
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * 啟用或禁用側邊抽屜和漢堡圖示
     * @param enabled true 為啟用，false 為禁用
     */
    public void setDrawerEnabled(boolean enabled) {
        if (drawerLayout == null || toggle == null) return;

        // 如果啟用，解鎖抽屜；如果禁用，鎖定抽屜
        int lockMode = enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
        drawerLayout.setDrawerLockMode(lockMode);

        // 啟用或禁用漢堡圖示
        toggle.setDrawerIndicatorEnabled(enabled);

        // 如果禁用漢堡圖示，則顯示返回箭頭
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(!enabled);
        }

        toggle.syncState();
    }
}
