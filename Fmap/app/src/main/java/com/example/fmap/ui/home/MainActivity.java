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
import com.example.fmap.ui.home.chat.ChatFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton; // ✨ 匯入 FAB

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
    private FloatingActionButton fabChat;
    private View chatContainer;

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
                    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                    toggle.setDrawerIndicatorEnabled(false);
                    getSupportActionBar().setDisplayShowTitleEnabled(true);
                }
            }
        });

        // ✨ 2. 新增：找到並設定懸浮聊天功能
        fabChat = findViewById(R.id.fab_chat);
        chatContainer = findViewById(R.id.chat_fragment_container);
        setupChatFragment();
        setupFab();
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
        }
        else if (id == android.R.id.home) {
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
                // ✨ 4. 新增：優先處理聊天視窗的關閉
                if (chatContainer != null && chatContainer.getVisibility() == View.VISIBLE) {
                    closeChatFragment();
                }
                // --- 以下為你原有的邏輯，保持不變 ---
                else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
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

        int lockMode = enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
        drawerLayout.setDrawerLockMode(lockMode);

        toggle.setDrawerIndicatorEnabled(enabled);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(!enabled);
        }

        toggle.syncState();
    }


    // --- ✨ 5. 新增：以下為控制懸浮聊天功能的全新方法 ✨ ---

    /**
     * 初始化 ChatFragment，並在預設情況下隱藏它。
     */
    private void setupChatFragment() {
        // 檢查 Fragment 是否已存在 (例如螢幕旋轉後)，如果不存在才新增
        if (getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container) == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.chat_fragment_container, new ChatFragment())
                    .commitNow(); // 使用 commitNow() 確保 Fragment 立即被建立
        }
        // 無論如何，初始狀態下都隱藏聊天容器
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
    }

    /**
     * 設定懸浮按鈕的點擊事件。
     */
    private void setupFab() {
        if (fabChat == null) return;
        fabChat.setOnClickListener(view -> {
            // 點擊後，顯示聊天容器，並隱藏懸浮按鈕自己
            if (chatContainer != null) {
                chatContainer.setVisibility(View.VISIBLE);
            }
            fabChat.hide();
        });
    }

    /**
     * 提供給 ChatFragment 呼叫的公開方法，用來關閉聊天視窗。
     */
    public void closeChatFragment() {
        // 隱藏聊天容器，並把懸浮按鈕顯示回來
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
        if (fabChat != null) {
            fabChat.show();
        }
    }
}
