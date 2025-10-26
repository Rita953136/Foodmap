// 檔案路徑: C:/Users/rita9/Documents/Foodmap/Fmap/app/src/main/java/com/example/fmap/ui/home/MainActivity.java
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

    // === 發送到 ChatFragment 用的 debounce ===
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tagsDebounceTask = null;
    private static final long TAGS_DEBOUNCE_MS = 250;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
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
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof TrashFragment) {
                getOnBackPressedDispatcher().onBackPressed();
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TrashFragment())
                        .addToBackStack(null)
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

            // 1) 原本：通知 VM 做本地篩選
            homeVM.applyTagFilter(new ArrayList<>(selectedSet));

            // 2) 同步到 ChatFragment（顯示附件小卡，支援叉叉回呼）
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.chat_fragment_container);
            if (f instanceof ChatFragment) {
                ChatFragment cf = (ChatFragment) f;

                // 設定（或覆蓋）一次回呼：WebView 點叉叉 → 取消對應 Chip
                cf.setOnTagRemoveListener(tag -> {
                    ChipGroup g2 = findViewById(R.id.chip_group_tags);
                    if (g2 == null) return;
                    for (int i = 0; i < g2.getChildCount(); i++) {
                        View v2 = g2.getChildAt(i);
                        if (v2 instanceof Chip) {
                            Chip chip = (Chip) v2;
                            CharSequence tx = chip.getText();
                            if (tx != null && tx.toString().trim().equals(tag)) {
                                chip.setChecked(false); // 會觸發本方法，再次同步 VM + WebView
                                break;
                            }
                        }
                    }
                });

                // debounce 推送（避免 evaluateJavascript 太頻繁）
                if (tagsDebounceTask != null) handler.removeCallbacks(tagsDebounceTask);
                final List<String> listForUi = new ArrayList<>(selectedSet);
                tagsDebounceTask = () -> cf.updateVisibleTags(listForUi);
                handler.postDelayed(tagsDebounceTask, TAGS_DEBOUNCE_MS);
            }
        });
    }

    // 按一下 FAB 打開聊天時，同步一次目前已選標籤
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

    // ====== Bottom Nav ======
    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (bottomNav.getSelectedItemId() == item.getItemId()) return false;

            Fragment target = null;
            int id = item.getItemId();
            if (id == R.id.home)      target = new HomeFragment();
            else if (id == R.id.map)  target = new MapFragment();
            else if (id == R.id.favorite) target = new FavoriteFragment();

            if (target != null) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, target)
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
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
    }

    public void closeChatFragment() {
        if (chatContainer != null) chatContainer.setVisibility(View.GONE);
        if (fabChat != null) fabChat.show();
    }
}
