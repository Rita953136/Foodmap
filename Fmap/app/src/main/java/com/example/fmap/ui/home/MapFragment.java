package com.example.fmap.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.fmap.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap map;
    private FusedLocationProviderClient fusedClient;
    private ActivityResultLauncher<String[]> permissionLauncher;

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

        // Play Services check (避免在沒有/過期時直接崩潰)
        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext());
        if (status != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(requireActivity(), status, 1001).show();
            return;
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        // 權限請求器
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

        // 動態加入地圖 Fragment
        final String TAG_MAP = "child_map";
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentByTag(TAG_MAP);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map_container, mapFragment, TAG_MAP)
                    .commitNow();
        }
        mapFragment.getMapAsync(this);

        // 回到我的位置按鈕
        FloatingActionButton fab = view.findViewById(R.id.fab_my_location);
        if (fab != null) {
            fab.setOnClickListener(v -> enableMyLocationAndCenter());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.map = googleMap;

        UiSettings ui = map.getUiSettings();
        ui.setZoomControlsEnabled(true);
        ui.setCompassEnabled(true);
        ui.setMapToolbarEnabled(true);
        ui.setAllGesturesEnabled(true);
        ui.setMyLocationButtonEnabled(true); // 顯示 Google Map 內建的「定位」按鈕

        map.setOnPoiClickListener(new GoogleMap.OnPoiClickListener() {
            @Override
            public void onPoiClick(@NonNull PointOfInterest poi) {
                map.addMarker(new MarkerOptions()
                        .position(poi.latLng)
                        .title(poi.name))
                        .showInfoWindow();
            }
        });

        // 先嘗試開啟定位（如果授權會自動置中）
        enableMyLocationAndCenter();

        // 沒授權/拿不到位置就放一個預設標記（台北 101）避免空白
        LatLng starter = new LatLng(25.0340, 121.5645);
        map.addMarker(new MarkerOptions().position(starter).title("Hello Map"));
    }

    /** 啟用我的位置圖層，並嘗試移動鏡頭到目前位置 */
    private void enableMyLocationAndCenter() {
        if (map == null) return;

        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!(fineGranted || coarseGranted)) {
            // 要求權限
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        try {
            map.setMyLocationEnabled(true);
            // 取最後一次位置並移動鏡頭
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng me = new LatLng(location.getLatitude(), location.getLongitude());
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15f));
                } else {
                    // 沒拿到就不強制移動，讓使用者自行拖曳或按內建定位鈕
                }
            });
        } catch (SecurityException ignored) { }
    }
}
