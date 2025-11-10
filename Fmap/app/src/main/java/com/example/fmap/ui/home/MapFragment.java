package com.example.fmap.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragmentDebug";

    private GoogleMap map;
    private FusedLocationProviderClient fusedClient;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private HomeViewModel homeViewModel;

    private final Map<String, Marker> markerById = new HashMap<>();
    private BitmapDescriptor bluePinIcon; // 圖示快取

    // ---- 固定相機測試點（不影響店家顯示） ----
    private static final boolean DEV_FIX_LOCATION = true;
    private static final LatLng DEV_POINT = new LatLng(24.1658, 120.6422);
    private static final float DEV_ZOOM = 15f;

    // 顯示範圍圓
    private static final double SHOW_WITHIN_KM = 0.0;
    private Circle rangeCircle;

    // 地圖尚未 ready 時先緩存資料
    private List<Place> pendingPlaces = new ArrayList<>();

    private EditText etSearch;
    private ImageButton btnSearch, btnClear;

    // ====== 接收詳情頁帶來的參數（定位與高亮） ======
    @Nullable private LatLng argCenter;
    @Nullable private String argStoreId;
    @Nullable private String argStoreName;
    @Nullable private Place argFallbackPlace; // 找不到 marker 時直接開詳情
    private boolean autoDetailPending = false; // 只自動開一次

    // 若事件來時尚未畫 marker，先暫存，renderMarkers 後再聚焦
    @Nullable private String pendingFocusPlaceId = null;

    // 地圖 padding（避開搜尋列與底部導覽列）
    private int pendingTopPadding = -1;

    // ====== 後端入口（你的 ngrok）與 JSON ======
    private static final String ENDPOINT =
            "https://rattly-excuseless-judie.ngrok-free.dev/api/tools/stores_search";
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    // HTTP client 與「是否已送過首次請求」旗標
    private final OkHttpClient http = new OkHttpClient();
    private boolean firstQuerySent = false;

    // ====== 上次座標快取（沿用策略） ======
    private static final String PREF = "loc_pref";
    private static final String K_LAT="last_lat", K_LNG="last_lng", K_ACC="last_acc", K_TS="last_ts";

    public MapFragment() { }
    public static MapFragment newInstance() { return new MapFragment(); }

    // ---------------- Lifecycle ----------------

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1) ViewModel
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        // 2) 參數
        handleArguments();

        // 3) 檢查 Google Play Services
        if (!checkGooglePlayServices()) return;

        // 4) UI & 定位
        setupViews(view);
        setupLocationServices();

        // 5) Map
        setupMapFragment();

        // 6) 觀察 VM
        observeViewModel();
    }

    // ---------------- Helpers (setup/args) ----------------

    private void handleArguments() {
        Bundle args = getArguments();
        if (args == null) return;

        double lat = args.getDouble("center_lat", Double.NaN);
        double lng = args.getDouble("center_lng", Double.NaN);
        if (!Double.isNaN(lat) && !Double.isNaN(lng)) argCenter = new LatLng(lat, lng);

        argStoreId   = args.getString("store_id", null);
        argStoreName = args.getString("store_name", null);
        Object fp = args.getSerializable("fallback_place");
        if (fp instanceof Place) argFallbackPlace = (Place) fp;

        autoDetailPending = (argStoreId != null || argStoreName != null || argCenter != null || argFallbackPlace != null);
    }

    private boolean checkGooglePlayServices() {
        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext());
        if (status != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), status, 1001).show();
            return false;
        }
        return true;
    }

    private void setupViews(@NonNull View view) {
        etSearch = view.findViewById(R.id.et_map_search);
        btnSearch = view.findViewById(R.id.btn_map_search);
        btnClear  = view.findViewById(R.id.btn_map_clear);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handleSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        btnSearch.setOnClickListener(v -> handleSearch(etSearch.getText().toString().trim()));

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            homeViewModel.applySearchQuery("");
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
        });

        // 搜尋列高度 → map padding
        final View searchBar = view.findViewById(R.id.map_search_bar);
        if (searchBar != null) {
            searchBar.post(() -> {
                int top = searchBar.getHeight() + dp(16);
                pendingTopPadding = top;
                if (map != null) map.setPadding(0, top, dp(8), dp(88));
            });
        }
    }

    private void setupLocationServices() {
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) {
                        enableMyLocationAndCenter(); // 只啟用圖層/按鈕，不移動鏡頭
                        // 成功授權：若尚未送出首次查詢，立刻做一次（沿用策略內建）
                        if (!firstQuerySent) acquireLocationThenSearch(false, getCurrentTags());
                    } else {
                        Toast.makeText(requireContext(), "未授權定位權限，無法顯示我的位置", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupMapFragment() {
        final String TAG_MAP = "child_map";
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentByTag(TAG_MAP);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map_container, mapFragment, TAG_MAP)
                    .commitNow();
        }
        mapFragment.getMapAsync(this);
    }

    private void observeViewModel() {
        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            int n = places == null ? 0 : places.size();
            Log.d(TAG, "observer getPlaces() size=" + n);

            pendingPlaces = (places == null) ? new ArrayList<>() : new ArrayList<>(places);
            if (map != null) renderMarkers(pendingPlaces);
        });

        List<Place> cur = homeViewModel.getPlaces().getValue();
        if (cur != null && !cur.isEmpty()) {
            pendingPlaces = new ArrayList<>(cur);
            if (map != null) renderMarkers(pendingPlaces);
        } else {
            homeViewModel.applySearchQuery("");
        }

        observeFocusRequests();
    }

    // ---------------- Google Map ----------------

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (!isAdded()) return;
        this.map = googleMap;
        Log.d(TAG, "onMapReady()");

        // 圖示快取（Vector→Bitmap）
        if (bluePinIcon == null) bluePinIcon = bluePin(R.drawable.ic_store_pin);

        UiSettings ui = this.map.getUiSettings();
        ui.setZoomControlsEnabled(true);
        ui.setCompassEnabled(true);
        ui.setMapToolbarEnabled(true);
        ui.setAllGesturesEnabled(true);
        ui.setMyLocationButtonEnabled(true); // 內建定位按鈕

        // 啟用藍點與內建按鈕（不移動鏡頭）
        enableMyLocationAndCenter();

        // 首次進入頁面 → 若尚未送過，嘗試做一次查詢（取得新定位或沿用上次座標）
        if (!firstQuerySent) acquireLocationThenSearch(false, getCurrentTags());

        // 初始鏡頭：argCenter > DEV_POINT > 不動
        if (argCenter != null) {
            this.map.moveCamera(CameraUpdateFactory.newLatLngZoom(argCenter, 16f));
        } else if (DEV_FIX_LOCATION) {
            this.map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
            drawRangeCircle();
        }

        // 若先量到 padding 但 map 未就緒，這裡補上
        if (pendingTopPadding >= 0) map.setPadding(0, pendingTopPadding, dp(8), dp(88));

        // marker 點擊
        this.map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Place) {
                Place p = (Place) tag;
                PlaceDetailFragment.newInstance(p.getId(), p)
                        .setSource(PlaceDetailFragment.SOURCE_MAP)
                        .show(getChildFragmentManager(), "PlaceDetailFragmentSheet");
            }
            return true;
        });

        // 監聽內建定位按鈕：保留預設行為 + 追加查詢（即時更新）
        this.map.setOnMyLocationButtonClickListener(() -> {
            Toast.makeText(requireContext(), "正在聚焦到我的位置...", Toast.LENGTH_SHORT).show();
            acquireLocationThenSearch(true, getCurrentTags()); // 移動鏡頭 + 送查詢
            return false; // 讓 Google Map 預設鏡頭行為繼續
        });

        // 畫面上畫既有資料
        if (pendingPlaces != null && !pendingPlaces.isEmpty()) {
            renderMarkers(pendingPlaces);
        } else {
            List<Place> cur2 = homeViewModel.getPlaces().getValue();
            if (cur2 != null && !cur2.isEmpty()) {
                pendingPlaces = new ArrayList<>(cur2);
                renderMarkers(pendingPlaces);
            }
        }
    }

    private BitmapDescriptor bluePin(int vectorResId) {
        Drawable d = ContextCompat.getDrawable(requireContext(), vectorResId);
        if (d == null) return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        d = d.mutate();
        d.setTint(Color.parseColor("#42A5F5"));
        int w = Math.max(d.getIntrinsicWidth(), 96);
        int h = Math.max(d.getIntrinsicHeight(), 96);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bm);
    }

    private void renderMarkers(List<Place> places) {
        if (map == null) return;
        int n = places == null ? 0 : places.size();
        Log.d(TAG, "renderMarkers() size=" + n);

        map.clear();
        markerById.clear();
        drawRangeCircle();

        if (n == 0) {
            if (argCenter != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(argCenter, 17f));
            }
            return;
        }

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int added = 0;

        for (Place p : places) {
            if (p == null) continue;
            Double lat = p.getLat();
            Double lng = p.getLng();
            if (lat == null || lng == null) continue;
            if (lat == 0.0 && lng == 0.0) continue;

            LatLng pos = new LatLng(lat, lng);

            if (DEV_FIX_LOCATION && SHOW_WITHIN_KM > 0) {
                double km = distanceKm(DEV_POINT.latitude, DEV_POINT.longitude, lat, lng);
                if (km > SHOW_WITHIN_KM) continue;
            }

            String title = p.getName() != null ? p.getName() : "";
            String addr  = p.getAddress() != null ? p.getAddress() : "";

            MarkerOptions opts = new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(addr)
                    .icon(bluePinIcon != null ? bluePinIcon
                            : BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));

            Marker m = map.addMarker(opts);
            if (m != null) {
                m.setTag(p);
                if (p.getId() != null)   markerById.put(p.getId(), m);
                if (p.getName() != null) markerById.put(p.getName(), m);
                bounds.include(pos);
                added++;
            }
        }

        // 事件聚焦
        if (pendingFocusPlaceId != null) {
            if (tryFocusOn(pendingFocusPlaceId)) {
                pendingFocusPlaceId = null;
                return;
            }
        }

        // 用 id/name 聚焦
        Marker focus = null;
        if (argStoreId != null) focus = markerById.get(argStoreId);
        if (focus == null && argStoreName != null) focus = markerById.get(argStoreName);
        if (focus != null) {
            focus.showInfoWindow();
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(focus.getPosition(), 17f));
            if (autoDetailPending) {
                autoDetailPending = false;
                Object tag = focus.getTag();
                if (tag instanceof Place) {
                    Place p = (Place) tag;
                    PlaceDetailFragment.newInstance(p.getId(), p)
                            .setSource(PlaceDetailFragment.SOURCE_MAP)
                            .show(getChildFragmentManager(), "PlaceDetailFragmentSheet");
                }
            }
            return;
        }

        // 找不到 marker 但有 fallback → 仍自動開詳情
        if (autoDetailPending && argFallbackPlace != null) {
            autoDetailPending = false;
            if (argCenter != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(argCenter, 17f));
            }
            PlaceDetailFragment.newInstance(argFallbackPlace.getId(), argFallbackPlace)
                    .setSource(PlaceDetailFragment.SOURCE_MAP)
                    .show(getChildFragmentManager(), "PlaceDetailFragmentSheet");
            return;
        }

        // 沒有 → 若有中心座標，放一顆紅標
        if (argCenter != null) {
            Marker temp = map.addMarker(new MarkerOptions()
                    .position(argCenter)
                    .title(argStoreName != null ? argStoreName : "目標位置")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            if (temp != null) temp.showInfoWindow();
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(argCenter, 17f));
            return;
        }

        // 一般情況：縮到全部可見或回固定點
        if (added > 0) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80));
        } else if (DEV_FIX_LOCATION) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
        }
    }

    //觀察 ViewModel 的聚焦事件
    private void observeFocusRequests() {
        homeViewModel.getNavigateToMapAndFocusOn().observe(getViewLifecycleOwner(), event -> {
            String placeIdToFocus = event.getContentIfNotHandled();
            if (placeIdToFocus == null) return;
            if (!tryFocusOn(placeIdToFocus)) {
                pendingFocusPlaceId = placeIdToFocus;
            }
        });
    }

    // 嘗試聚焦指定 id 的 marker，成功回傳 true
    private boolean tryFocusOn(@NonNull String placeId) {
        if (map == null) return false;
        Marker m = markerById.get(placeId);
        if (m == null) return false;
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(m.getPosition(), 17f));
        m.showInfoWindow();
        return true;
    }

    private void drawRangeCircle() {
        if (!DEV_FIX_LOCATION || map == null) return;
        if (SHOW_WITHIN_KM <= 0) return;

        if (rangeCircle != null) {
            rangeCircle.remove();
            rangeCircle = null;
        }
        rangeCircle = map.addCircle(new CircleOptions()
                .center(DEV_POINT)
                .radius(SHOW_WITHIN_KM * 1000.0)
                .strokeWidth(2f)
                .strokeColor(0xFF4285F4)
                .fillColor(0x224285F4));
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /**
     * 只負責：啟用藍點與內建定位按鈕。不自動移動鏡頭。
     */
    private void enableMyLocationAndCenter() {
        if (map == null) return;

        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!(fineGranted || coarseGranted)) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        try {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
        } catch (SecurityException ignored) { }
    }

    /** 搜尋處理（背景執行 Geocoder；主執行緒更新地圖或 Toast） */
    private void handleSearch(@NonNull String query) {
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入搜尋關鍵字", Toast.LENGTH_SHORT).show();
            return;
        }
        homeViewModel.applySearchQuery(query);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.TRADITIONAL_CHINESE);
                List<Address> list = geocoder.getFromLocationName(query, 1);

                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    LatLng pos = new LatLng(a.getLatitude(), a.getLongitude());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                        });
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "找不到符合的地點", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Geocoder 搜尋失敗", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "搜尋服務異常，請稍後再試", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        try {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setDrawerIconEnabled(false);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        try {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setDrawerIconEnabled(true);
            }
        } catch (Throwable ignored) {}
    }

    private int dp(int dps) {
        return (int) (dps * getResources().getDisplayMetrics().density);
    }

    // ====== 位置沿用策略：儲存 / 讀取上次座標 ======
    private void saveLastLocation(double lat, double lng, float acc, long tsMs){
        requireContext().getSharedPreferences(PREF, 0)
                .edit()
                .putLong(K_TS, tsMs)
                .putFloat(K_ACC, acc)
                .putString(K_LAT, String.valueOf(lat))
                .putString(K_LNG, String.valueOf(lng))
                .apply();
    }

    @Nullable
    private Location loadLastLocation(){
        var sp = requireContext().getSharedPreferences(PREF, 0);
        if(!sp.contains(K_LAT) || !sp.contains(K_LNG)) return null;
        Location loc = new Location("cached");
        loc.setLatitude(Double.parseDouble(sp.getString(K_LAT,"0")));
        loc.setLongitude(Double.parseDouble(sp.getString(K_LNG,"0")));
        loc.setAccuracy(sp.getFloat(K_ACC, 0f));
        loc.setTime(sp.getLong(K_TS, System.currentTimeMillis()));
        return loc;
    }

    // ====== 核心：取得一次定位 → 儲存 → 送查詢（首次和按定位都用它） ======
    private void acquireLocationThenSearch(boolean moveCamera, @NonNull List<String> tags){
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!(fineGranted || coarseGranted)) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        if (fusedClient == null) fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());
        CancellationTokenSource cts = new CancellationTokenSource();

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    Location useLoc = loc;
                    if (useLoc == null) {
                        // 取不到 → 沿用上次
                        useLoc = loadLastLocation();
                        if (useLoc == null) {
                            Toast.makeText(requireContext(),"尚未取得定位，請按定位一次",Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        // 取到新值 → 儲存以便下次沿用
                        saveLastLocation(useLoc.getLatitude(), useLoc.getLongitude(),
                                useLoc.getAccuracy(), System.currentTimeMillis());
                    }

                    if (moveCamera && map != null){
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(useLoc.getLatitude(), useLoc.getLongitude()), 16f));
                    }

                    postStoresSearch(useLoc, tags, false); // rank_by_distance 固定 false（依需求）
                    firstQuerySent = true;
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),"定位失敗："+e.getMessage(),Toast.LENGTH_SHORT).show());
    }

    // ====== 送出 stores_search 請求（完全照你的協議） ======
    private void postStoresSearch(@NonNull Location loc,
                                  @NonNull List<String> tags,
                                  boolean rankByDistance){
        try {
            JSONObject root = new JSONObject();
            root.put("intent", "store_by_tag");

            org.json.JSONArray arr = new org.json.JSONArray();
            for(String t: tags) arr.put(t);
            root.put("tags", arr);

            JSONObject locObj = new JSONObject();
            locObj.put("lat", loc.getLatitude());
            locObj.put("lng", loc.getLongitude());
            locObj.put("accuracy_m", (double)loc.getAccuracy());
            locObj.put("source", "android_gps");
            locObj.put("timestamp", System.currentTimeMillis());
            root.put("location", locObj);

            root.put("rank_by_distance", rankByDistance);

            RequestBody body = RequestBody.create(root.toString(), JSON);
            Request req = new Request.Builder()
                    .url(ENDPOINT)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),"搜尋失敗："+e.getMessage(),Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response resp) throws IOException {
                    String respBody = resp.body()!=null ? resp.body().string() : "";
                    resp.close();
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (resp.isSuccessful()) {
                            // TODO: 把 respBody 丟給 VM/Adapter，使用 results[*].distance_km/matched[*].distance_km 顯示
                            Toast.makeText(requireContext(), "搜尋成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "搜尋 HTTP "+resp.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), "封包錯誤："+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ====== 取得目前 tags：優先從 HomeViewModel 讀，否則退回 ["咖哩"] ======
    @NonNull
    private List<String> getCurrentTags(){
        // 嘗試讀取 homeViewModel.getSelectedTags() 的 LiveData<List<String>>
        try {
            List<String> vm = homeViewModel.getSelectedTags().getValue();
            if (vm != null && !vm.isEmpty()) return vm;
        } catch (Throwable ignored) {}
        // Fallback：示範用，請換成你要的預設
        return java.util.Arrays.asList("咖哩");
    }
}
