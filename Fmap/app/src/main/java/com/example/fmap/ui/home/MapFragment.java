package com.example.fmap.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.location.Address;
import android.location.Geocoder;
import java.util.Locale;
import java.io.IOException;


public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragmentDebug";

    private GoogleMap map;
    private FusedLocationProviderClient fusedClient;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private HomeViewModel homeViewModel;

    private final Map<String, Marker> markerById = new HashMap<>();

    // ---- 固定相機測試點（不影響店家顯示） ----
    private static final boolean DEV_FIX_LOCATION = true;
    private static final LatLng DEV_POINT = new LatLng(24.1658, 120.6422);
    private static final float DEV_ZOOM = 15f;

    // 先顯示全部，避免把點過濾掉
    private static final double SHOW_WITHIN_KM = 0.0;
    private Circle rangeCircle;
    // 重要：用來解決「資料先到、地圖還沒 ready」的時序問題
    private List<Place> pendingPlaces = new ArrayList<>();
    // 放大到幾倍顯示店名（可自行調 15.5~17 之間）
    private static final float LABEL_ZOOM_THRESHOLD = 16.5f;
    // 目前是否已顯示「店名標籤」狀態（避免每次移動都重算）
    private boolean labelsShown = false;
    // 快取：店名 → 文字圖示，避免每次放大都重繪
    private final Map<String, BitmapDescriptor> labelIconCache = new HashMap<>();
    private EditText etSearch;
    private ImageButton btnSearch, btnClear;
    public MapFragment() { }

    public static MapFragment newInstance() { return new MapFragment(); }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext());
        if (status != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), status, 1001).show();
            return;
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) {
                        enableMyLocationAndCenter();
                    } else {
                        Toast.makeText(requireContext(), "未授權定位權限，無法顯示我的位置", Toast.LENGTH_SHORT).show();
                    }
                });

        final String TAG_MAP = "child_map";
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentByTag(TAG_MAP);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map_container, mapFragment, TAG_MAP)
                    .commitNow();
        }
        mapFragment.getMapAsync(this);

        etSearch = view.findViewById(R.id.et_map_search);
        btnSearch = view.findViewById(R.id.btn_map_search);
        btnClear  = view.findViewById(R.id.btn_map_clear);

        // 鍵盤搜尋鍵
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handleSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        // 點擊放大鏡
        btnSearch.setOnClickListener(v2 -> handleSearch(etSearch.getText().toString().trim()));

        // 清除：清文字＋清關鍵字篩選＋回到固定點（若你開了 DEV_FIX_LOCATION）
        btnClear.setOnClickListener(v3 -> {
            etSearch.setText("");
            homeViewModel.applySearchQuery("");   // 重設清單（你的 HomeViewModel 會重載 places）
            if (map != null) {
                // 視情況決定回到目前相機中心或固定點
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
            }
        });


        // 觀察店家：資料來就畫，並記錄 Log
        homeViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> {
            int n = places == null ? 0 : places.size();
            Log.d(TAG, "observer getPlaces() size=" + n);
            Toast.makeText(requireContext(), "店家數：" + n, Toast.LENGTH_SHORT).show();

            // 緩存最新資料；如果地圖還沒 ready，先存起來，等 ready 後再畫
            pendingPlaces = (places == null) ? new ArrayList<>() : new ArrayList<>(places);

            if (map != null) {
                renderMarkers(pendingPlaces);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.map = googleMap;
        Log.d(TAG, "onMapReady()");

        UiSettings ui = map.getUiSettings();
        ui.setZoomControlsEnabled(true);
        ui.setCompassEnabled(true);
        ui.setMapToolbarEnabled(true);
        ui.setAllGesturesEnabled(true);

        if (DEV_FIX_LOCATION) {
            ui.setMyLocationButtonEnabled(false);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
            drawRangeCircle();
        } else {
            ui.setMyLocationButtonEnabled(true);
            enableMyLocationAndCenter();
        }

        map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Place) {
                Place p = (Place) tag;
                PlaceDetailFragment.newInstance(p.getId(), p)
                        .show(getChildFragmentManager(), "PlaceDetailFragmentSheet");
            }
            return true;
        });

        // 關鍵：地圖準備好後，立刻用「目前持有的清單」畫一次
        if (pendingPlaces != null && !pendingPlaces.isEmpty()) {
            Log.d(TAG, "onMapReady(): draw pending places size=" + pendingPlaces.size());
            renderMarkers(pendingPlaces);
        } else {
            // 若 ViewModel 早已準備好資料，這裡再取一次
            List<Place> cur = homeViewModel.getPlaces().getValue();
            Log.d(TAG, "onMapReady(): draw places from VM size=" + (cur == null ? 0 : cur.size()));
            if (cur != null && !cur.isEmpty()) {
                pendingPlaces = new ArrayList<>(cur);
                renderMarkers(pendingPlaces);
            }
        }
    }

    private void renderMarkers(List<Place> places) {
        if (map == null) return;
        int n = places == null ? 0 : places.size();
        Log.d(TAG, "renderMarkers() size=" + n);

        map.clear();
        markerById.clear();
        drawRangeCircle();

        if (n == 0) return;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int added = 0;

        for (Place p : places) {
            if (p == null) continue;
            Double lat = p.getLat();
            Double lng = p.getLng();
            if (lat == null || lng == null) continue;
            if (lat == 0.0 && lng == 0.0) continue; // 防 0,0

            LatLng pos = new LatLng(lat, lng);

            if (DEV_FIX_LOCATION && SHOW_WITHIN_KM > 0) {
                double km = distanceKm(DEV_POINT.latitude, DEV_POINT.longitude, lat, lng);
                if (km > SHOW_WITHIN_KM) continue;
            }

            String title = p.getName() != null ? p.getName() : (p.getName() != null ? p.getName() : "");
            String addr  = p.getAddress() != null ? p.getAddress() : (p.getAddress() != null ? p.getAddress() : "");

            MarkerOptions opts = new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(addr)
                    .icon(bluePin(R.drawable.ic_store_pin));


            Marker m = map.addMarker(opts);
            if (m != null) {
                m.setTag(p);
                if (p.id != null) markerById.put(p.id, m);
                bounds.include(pos);
                added++;
            }
        }

        Log.d(TAG, "renderMarkers() added markers=" + added);
        if (added > 0) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80));
        } else if (DEV_FIX_LOCATION) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
        }
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

    private void enableMyLocationAndCenter() {
        if (map == null) return;

        if (DEV_FIX_LOCATION) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(DEV_POINT, DEV_ZOOM));
            return;
        }

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
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng me = new LatLng(location.getLatitude(), location.getLongitude());
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15f));
                }
            });
        } catch (SecurityException ignored) { }
    }
    /** 固定淡藍色圖釘（使用 vector pin 圖示） */
    private BitmapDescriptor bluePin(int vectorResId) {
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(requireContext(), vectorResId);
        if (d == null) return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        d = d.mutate();

        // 固定顏色：Google Map Style 淡藍 #42A5F5
        int color = android.graphics.Color.parseColor("#42A5F5");
        d.setTint(color);

        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0) w = 96;
        if (h <= 0) h = 96;

        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bm);
    }
    /** 搜尋處理：同時做「資料篩選」與「地圖移動」 */
    private void handleSearch(@NonNull String query) {
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入關鍵字或地址", Toast.LENGTH_SHORT).show();
            return;
        }
        // 1) 讓 HomeViewModel 依關鍵字重查，markers 會跟著 observer → renderMarkers()
        homeViewModel.applySearchQuery(query);

        // 2) 嘗試把地圖移到這個文字描述的位置（像地址/地標）
        geocodeAndMove(query);
    }

    /** 以 Geocoder 嘗試把字串轉成座標並移動鏡頭 */
    private void geocodeAndMove(String query) {
        if (map == null) return;
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            // 取第一筆匹配結果（可視需求改多筆）
            java.util.List<Address> list = geocoder.getFromLocationName(query, 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                LatLng pos = new LatLng(a.getLatitude(), a.getLongitude());
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
            } else {
                // 找不到也沒關係，仍然會透過 applySearchQuery 篩選你的店家
                Toast.makeText(requireContext(), "找不到此位置，已改用清單篩選", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            // 某些模擬器無網路/無 geocoder 資料時可能會進到這裡
            Toast.makeText(requireContext(), "定位服務暫時無法使用，已改用清單篩選", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerIconEnabled(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDrawerIconEnabled(true);
        }
    }
}
