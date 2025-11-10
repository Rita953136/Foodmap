package com.example.fmap.ui.home;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.example.fmap.ui.home.MapFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private BottomNavigationView bottomNav;
    private HomeViewModel homeVM;
    private ActionBarDrawerToggle toggle;
    private TextView tvTitle;
    private TextView tvDate;
    private FloatingActionButton fabChat;
    private View chatContainer;
    private float dX, dY;
    private long lastTouchDown;
    private static final int CLICK_ACTION_THRESHOLD = ViewConfiguration.getTapTimeout();
    private Toolbar toolbar;

    // === 發送到 ChatFragment 用的 debounce ===
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tagsDebounceTask = null;
    private static final long TAGS_DEBOUNCE_MS = 250;

    // 記錄目前多選標籤；關抽屜時一次送出
    private List<String> latestSelectedTags = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        tvTitle = findViewById(R.id.tvTitle);
        tvDate  = findViewById(R.id.tvDate);

        drawerLayout = findViewById(R.id.drawer_layout);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open_nav, R.string.close_nav);
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

        fabChat = findViewById(R.id.fab_chat);
        chatContainer = findViewById(R.id.chat_fragment_container);
        setupChatFragment();
        setupMovableFab();

        // ★Drawer 關閉時，一次把完整標籤送給 ChatFragment
        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
                @Override public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}
                @Override public void onDrawerOpened(@NonNull View drawerView) {}
                @Override public void onDrawerClosed(@NonNull View drawerView) {
                    Fragment f = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);
                    if (f instanceof ChatFragment) {
                        ((ChatFragment) f).applySelectedTags(new ArrayList<>(latestSelectedTags));
                    }
                }
                @Override public void onDrawerStateChanged(int newState) {}
            });
        }
    }

    // ====== 懸浮按鈕可拖曳 + 開關聊天 ======
    @SuppressLint("ClickableViewAccessibility")
    private void setupMovableFab() {
        if (fabChat == null) return;
        fabChat.setOnClickListener(v -> {
            if (chatContainer != null) chatContainer.setVisibility(View.VISIBLE);
            fabChat.hide();
            // 打開時把目前選中標籤同步一次到 ChatFragment
            pushSelectedTagsToChatFragment();
        });

        fabChat.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchDown = System.currentTimeMillis();
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;
                    View parent = (View) view.getParent();
                    newX = Math.max(0, Math.min(parent.getWidth() - view.getWidth(), newX));
                    newY = Math.max(0, Math.min(parent.getHeight() - view.getHeight(), newY));
                    view.animate().x(newX).y(newY).setDuration(0).start();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (System.currentTimeMillis() - lastTouchDown < CLICK_ACTION_THRESHOLD) {
                        view.performClick();
                    }
                    return true;
            }
            return false;
        });
    }

    // ====== 返回鍵邏輯 ======
    private void setupBackPressLogic() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                Fragment chatFragment = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);

                if (chatContainer != null && chatContainer.getVisibility() == View.VISIBLE) {
                    if (chatFragment instanceof ChatFragment && ((ChatFragment) chatFragment).onBackPressed()) {
                        return;
                    }
                    closeChatFragment();
                } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
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

    // ====== Toolbar 顯示 ======
    public void setHomeToolbar() {
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
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
                getSupportFragmentManager().popBackStack();
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TrashFragment())
                        .addToBackStack("toggle_trash")
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

    // ====== Drawer Chips → VM + 同步到 ChatFragment 可見小卡 ======
    private void setupDrawerChips() {
        ChipGroup chipGroup = findViewById(R.id.chip_group_tags);
        if (chipGroup == null) return;

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            final LinkedHashSet<String> selectedSet = new LinkedHashSet<>();
            for (Integer id : checkedIds) {
                Chip c = group.findViewById(id);
                if (c != null && c.getText() != null) {
                    String t = c.getText().toString().trim();
                    if (!t.isEmpty()) selectedSet.add(t);
                }
            }

            // 1) 通知 VM 做本地篩選
            homeVM.applyTagFilter(new ArrayList<>(selectedSet));

            // 2) 更新最新多選結果，待抽屜關閉時送出
            latestSelectedTags = new ArrayList<>(selectedSet);

            // 3) 同步到 ChatFragment（更新可見小卡，支援叉叉回呼）
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);
            if (f instanceof ChatFragment) {
                ChatFragment cf = (ChatFragment) f;

                // WebView 點叉叉 → 取消對應 Chip
                cf.setOnTagRemoveListener(tag -> {
                    ChipGroup g2 = findViewById(R.id.chip_group_tags);
                    if (g2 == null) return;
                    for (int i = 0; i < g2.getChildCount(); i++) {
                        View v2 = g2.getChildAt(i);
                        if (v2 instanceof Chip) {
                            Chip chip = (Chip) v2;
                            CharSequence tx = chip.getText();
                            if (tx != null && tx.toString().trim().equals(tag)) {
                                chip.setChecked(false);
                                break;
                            }
                        }
                    }
                });

                // debounce 推送 → 更新「可見標籤小卡」（不觸發 Agent）
                if (tagsDebounceTask != null) handler.removeCallbacks(tagsDebounceTask);
                final List<String> listForUi = new ArrayList<>(selectedSet);
                tagsDebounceTask = () -> cf.updateVisibleTags(listForUi);
                handler.postDelayed(tagsDebounceTask, TAGS_DEBOUNCE_MS);
            }
        });
    }


    // 打開聊天時，立刻同步一次目前標籤（顯示小卡）
    private void pushSelectedTagsToChatFragment() {
        ChipGroup chipGroup = findViewById(R.id.chip_group_tags);
        if (chipGroup == null) return;

        final LinkedHashSet<String> selectedSet = new LinkedHashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View v = chipGroup.getChildAt(i);
            if (v instanceof Chip) {
                Chip c = (Chip) v;
                if (c.isChecked() && c.getText() != null) {
                    String t = c.getText().toString().trim();
                    if (!t.isEmpty()) selectedSet.add(t);
                }
            }
        }

        Fragment f = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);
        if (f instanceof ChatFragment) {
            ((ChatFragment) f).updateVisibleTags(new ArrayList<>(selectedSet));
        }
    }

    public void setDrawerIconEnabled(boolean enabled) {
        if (enabled) {
            // 開啟抽屜功能
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            toggle.setDrawerIndicatorEnabled(true);   // 恢復 ActionBarDrawerToggle 的控制
            toggle.syncState();

            // 恢復亮度
            if (toolbar.getNavigationIcon() != null)
                toolbar.getNavigationIcon().setAlpha(255);

            // 點擊可以打開 Drawer
            toolbar.setNavigationOnClickListener(v ->
                    drawerLayout.openDrawer(GravityCompat.START));

        } else {
            // 關閉抽屜功能
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            toggle.setDrawerIndicatorEnabled(false); // 暫時停用 toggle

            // 手動放上同樣的漢堡圖示（灰掉）
            toolbar.setNavigationIcon(toggle.getDrawerArrowDrawable()); // 換成你的漢堡 icon 檔
            if (toolbar.getNavigationIcon() != null)
                toolbar.getNavigationIcon().setAlpha(80); // 變暗 (0~255)

            // 取消點擊
            toolbar.setNavigationOnClickListener(null);
        }
    }

    // ====== Bottom Nav ======
    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            // 觀察實際被點到的 id 名稱，方便檢查 bottom_menu.xml 是否一致
            try {
                String name = getResources().getResourceEntryName(item.getItemId());
                android.util.Log.d("BottomNav", "clicked: " + name + " (" + item.getItemId() + ")");
            } catch (Exception ignore) {}

            int id = item.getItemId();
            Fragment target = null;
            String tag = null;

            if (id == R.id.home) {
                target = new HomeFragment();
                tag = "HomeFragment";
            } else if (id == R.id.map) {
                target = new MapFragment();
                tag = "MapFragment";
            } else if (id == R.id.favorite) {
                target = new FavoriteFragment();
                tag = "FavoriteFragment";
            } else {
                // 沒對到任何已知 id，回傳 false 讓系統知道沒處理
                return false;
            }

            // 切頁
            getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragment_container, target, tag)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

            return true; // 一定要回傳 true，代表這次點擊已處理
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
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
    }

    public void closeChatFragment() {
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
        if (fabChat != null) fabChat.show();
    }
    // ====== 供其他 Fragment 呼叫：切換到地圖頁，並帶座標 ======
    public void openMapWithArgs(@Nullable Bundle args) {
        MapFragment map = new MapFragment();
        if (args != null) map.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, map, "MapFragment")
                .addToBackStack("MapFragment")
                .commit();

        // ★ 同步底部導覽顯示「地圖」
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.map); // ← 確認你的 bottom_menu.xml 地圖項目 id 是否叫 map
        }
    }

}
