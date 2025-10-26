// 檔案路徑: C:/Users/rita9/Documents/Foodmap/Fmap/app/src/main/java/com/example/fmap/ui/home/MainActivity.java
package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // ... (你原有的變數宣告保持不變)
    private DrawerLayout drawerLayout;
    private BottomNavigationView bottomNav;
    private HomeViewModel homeVM;
    private Toolbar toolbar;
    private ActionBarDrawerToggle toggle;

    private TextView tvTitle;
    private TextView tvDate;
    private FloatingActionButton fabChat;
    private View chatContainer;

    // ✨ 新增：用於拖曳按鈕的變數
    private float dX, dY;
    private long lastTouchDown;
    private static final int CLICK_ACTION_THRESHHOLD = 200;


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

        // ✨ 已更新為包含閃退修正的邏輯
        setupBackPressLogic();

        // 監聽 Fragment 變化 (保持不變)
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
                    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                    toggle.setDrawerIndicatorEnabled(false);
                    getSupportActionBar().setDisplayShowTitleEnabled(true);
                }
            }
        });

        // ✨ 已更新為包含可移動按鈕的邏輯
        fabChat = findViewById(R.id.fab_chat);
        chatContainer = findViewById(R.id.chat_fragment_container);
        setupChatFragment();
        setupMovableFab(); // <--- 方法名已修改
    }

    // --- 【方法已更新】 ---
    /**
     * 設定懸浮按鈕的觸控事件，使其可拖曳。
     */
    private void setupMovableFab() {
        if (fabChat == null) return;

        fabChat.setOnTouchListener((view, event) -> {
            // 取得按鈕所在的父容器 (通常是 CoordinatorLayout 或 FrameLayout)
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            View parentView = (View) view.getParent();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 記錄手指按下的時間和相對於按鈕左上角的偏移量
                    lastTouchDown = System.currentTimeMillis();
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    // 計算新的 x, y 座標
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    // 限制按鈕不能移出父容器的邊界
                    newX = Math.max(0, newX); // 左邊界
                    newX = Math.min(parentView.getWidth() - view.getWidth(), newX); // 右邊界
                    newY = Math.max(0, newY); // 上邊界
                    newY = Math.min(parentView.getHeight() - view.getHeight(), newY); // 下邊界

                    // 移動按鈕
                    view.animate()
                            .x(newX)
                            .y(newY)
                            .setDuration(0)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                    // 如果按下到放開的時間很短，視為一次「點擊」
                    if (System.currentTimeMillis() - lastTouchDown < CLICK_ACTION_THRESHHOLD) {
                        // 執行原本的點擊邏輯
                        if (chatContainer != null) {
                            chatContainer.setVisibility(View.VISIBLE);
                        }
                        fabChat.hide();
                    }
                    break;

                default:
                    return false;
            }
            // 返回 true 表示我們已經完整處理了這個觸控事件
            return true;
        });
    }

    // --- 【方法已更新】 ---
    /**
     * 修正返回邏輯，統一處理所有返回事件，避免閃退。
     */
    private void setupBackPressLogic() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment chatFragment = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);

                // 1. 優先處理聊天視窗
                if (chatContainer != null && chatContainer.getVisibility() == View.VISIBLE) {
                    if (chatFragment instanceof ChatFragment && ((ChatFragment) chatFragment).onBackPressed()) {
                        return; // WebView 處理了返回事件，結束
                    }
                    closeChatFragment();
                }
                // 2. 處理側邊抽屜
                else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                // 3. 處理 Fragment 返回堆疊
                else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                }
                // 4. 如果不在首頁，則切換回首頁
                else if (bottomNav.getSelectedItemId() != R.id.home) {
                    bottomNav.setSelectedItemId(R.id.home);
                }
                // 5. 如果在首頁，則結束 App
                else {
                    finish();
                }
            }
        });
    }


    // --- 以下是你原有的其他方法，保持不變 ---
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

    public void setDrawerEnabled(boolean enabled) {
        if (drawerLayout == null || toggle == null) return;
        int lockMode = enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
        drawerLayout.setDrawerLockMode(lockMode);
        toggle.setDrawerIndicatorEnabled(enabled);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(!enabled);
        }
        toggle.syncState();
    }

    private void setupChatFragment() {
        if (getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container) == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.chat_fragment_container, new ChatFragment())
                    .commitNow();
        }
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
    }

    // 這個方法在 setupMovableFab 中已經不需要，因此刪除
    // private void setupFab() { ... }

    public void closeChatFragment() {
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
        if (fabChat != null) {
            fabChat.show();
        }
    }
}
