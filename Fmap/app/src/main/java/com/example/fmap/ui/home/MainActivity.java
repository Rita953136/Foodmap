package com.example.fmap.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityResultLauncher<Intent> loginLauncher;
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private BottomNavigationView bottomNav;

    private GoogleSignInClient googleClient;      // 用於 Google Sign-Out
    private TextView tvHeaderName, tvHeaderEmail; // Drawer header 上的名稱/Email（若有）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 資料來源初始化（你原本就有）
        RepositoryProvider.init(new MockPlacesRepository(getAssets()));
        setContentView(R.layout.activity_main);

        // Toolbar + Drawer
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle =
                new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                        R.string.open_nav, R.string.close_nav);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 取 Drawer Header 的文字元件（若 layout 沒這兩個 id，這裡會拿到 null，不影響）
        tvHeaderName  = navView.getHeaderView(0).findViewById(R.id.headerName);
        tvHeaderEmail = navView.getHeaderView(0).findViewById(R.id.headerEmail);

        // GoogleSignInClient（僅用於 signOut）
        googleClient = GoogleSignIn.getClient(
                this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build()
        );

        // LoginActivity 回傳結果（成功登入回到這裡）
        loginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                (ActivityResult result) -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        updateAuthUi(); // 直接用 FirebaseAuth 取使用者並更新 UI
                        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.user);
                        Toast.makeText(MainActivity.this, "Logged in", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // BottomNavigation
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            // 清掉 Drawer push 上來的 back stack，避免遮住底部分頁
            getSupportFragmentManager()
                    .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

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
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.home);
            navView.setCheckedItem(R.id.nav_home);
        }

        // 依目前 Firebase 狀態刷新一次（Login/Logout 按鈕 + Header 文案）
        updateAuthUi();
    }

    /** 依 Firebase 登入狀態切換 Drawer 的 Login/Logout 與 Header 文字 */
    private void updateAuthUi() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();

        // 切換那顆動態按鈕（id 請用 nav_auth；若你沿用原本 id，記得一起改）
        MenuItem auth = navView.getMenu().findItem(R.id.nav_auth);
        if (auth != null) {
            boolean loggedIn = (u != null);
            auth.setTitle(loggedIn ? "Logout" : "Login");
            auth.setIcon(loggedIn ? R.drawable.outline_logout_24 : R.drawable.outline_login_24);
        }

        // Header 顯示（若你的 nav_header 有這兩個 id）
        if (tvHeaderName != null) {
            String name = (u != null && u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                    ? u.getDisplayName() : "Happy Food Map";
            tvHeaderName.setText(name);
        }
        if (tvHeaderEmail != null) {
            String mail = (u != null && u.getEmail() != null) ? u.getEmail() : "Tap to login";
            tvHeaderEmail.setText(mail);
        }
    }

    private Fragment switchTab(String tag, Fragment target) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        for (Fragment f : getSupportFragmentManager().getFragments()) {
            ft.hide(f);
        }

        Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        Fragment toShow = (existing != null) ? existing : target;

        if (existing == null) ft.add(R.id.fragment_container, toShow, tag);
        else ft.show(toShow);

        ft.commit();
        return toShow;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();

        } else if (id == R.id.nav_settings) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .addToBackStack("settings")
                    .commit();

        } else if (id == R.id.nav_share) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ShareFragment())
                    .addToBackStack("share")
                    .commit();

        } else if (id == R.id.nav_about) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AboutFragment(), "drawer_about")
                    .addToBackStack("drawer")
                    .commit();

        } else if (id == R.id.nav_auth) {
            // 單一顆動態按鈕：未登入→開 LoginActivity；已登入→登出
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                FirebaseAuth.getInstance().signOut();
                // 若之前用 Google 登入，同步登出 Google
                if (googleClient != null) googleClient.signOut();
                updateAuthUi();
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            } else {
                loginLauncher.launch(new Intent(this, LoginActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    public void selectBottomTab(int menuId) {
        if (bottomNav != null) bottomNav.setSelectedItemId(menuId);
    }
}
