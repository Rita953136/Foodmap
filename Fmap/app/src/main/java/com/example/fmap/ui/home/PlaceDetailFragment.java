package com.example.fmap.ui.home;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.data.local.StoreMappers;
import com.example.fmap.model.Place;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 店家詳情（純本機資料版） */
public class PlaceDetailFragment extends BottomSheetDialogFragment {

    private static final String ARG_PLACE_ID = "place_id";

    private FavoritesStore favoritesStore;
    private StoreRepository storeRepo;   // ← 用本機 repository
    private String placeId;
    private Place currentPlace;
    private boolean isCurrentlyFavorite = false;

    // Views
    private ImageView imgThumb;
    private TextView tvName, tvRating, tvMeta;
    private RatingBar ratingBar;
    private ChipGroup chipGroupTags;
    private MaterialButton btnNavigate, btnHeart;

    public static PlaceDetailFragment newInstance(String placeId) {
        PlaceDetailFragment fragment = new PlaceDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PLACE_ID, placeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            favoritesStore = FavoritesStore.getInstance(getContext());
        }
        // 以 Application 建立本機資料 Repository
        storeRepo = new StoreRepository(requireActivity().getApplication());

        if (getArguments() != null) {
            placeId = getArguments().getString(ARG_PLACE_ID);
        }
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_place_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupClickListeners();

        if (placeId != null) {
            loadPlaceDetailsLocal();
        } else {
            Toast.makeText(getContext(), "錯誤：店家 ID 為空", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private void initializeViews(@NonNull View view) {
        imgThumb      = view.findViewById(R.id.imgThumb);
        tvName        = view.findViewById(R.id.tvName);
        tvRating      = view.findViewById(R.id.tvRating);
        tvMeta        = view.findViewById(R.id.tvMeta);
        ratingBar     = view.findViewById(R.id.ratingBar);
        chipGroupTags = view.findViewById(R.id.chip_group_tags);
        btnNavigate   = view.findViewById(R.id.btnNavigate);
        btnHeart      = view.findViewById(R.id.btnHeart);
    }

    private void setupClickListeners() {
        btnNavigate.setOnClickListener(v -> openGoogleMaps());
        btnHeart.setOnClickListener(v -> toggleFavoriteStatus());
    }

    // ------- 資料載入（本機） -------
    private void loadPlaceDetailsLocal() {
        // 直接用 getByIds 查單筆，並轉為 Place
        storeRepo.getByIds(Collections.singletonList(placeId))
                .observe(getViewLifecycleOwner(), entities -> {
                    if (entities == null || entities.isEmpty()) {
                        Toast.makeText(getContext(), "找不到該店家資料", Toast.LENGTH_LONG).show();
                        dismiss();
                        return;
                    }
                    currentPlace = StoreMappers.toPlace(entities.get(0));
                    if (currentPlace == null) {
                        Toast.makeText(getContext(), "資料格式錯誤", Toast.LENGTH_LONG).show();
                        dismiss();
                        return;
                    }
                    // 收藏狀態
                    if (favoritesStore != null && currentPlace.getId() != null) {
                        isCurrentlyFavorite = favoritesStore.contains(currentPlace.getId());
                    }
                    updateHeartButtonUI();
                    bindPlaceToViews(currentPlace);
                });
    }

    private void bindPlaceToViews(@NonNull Place p) {
        // 圖片
        Glide.with(this)
                .load(p.getCoverImage())
                .placeholder(R.color.material_dynamic_neutral90)
                .centerCrop()
                .into(imgThumb);

        // 名稱
        tvName.setText(p.getName() != null ? p.getName() : "未命名店家");

        // 評分
        double ratingValue = (p.getRating() != null) ? p.getRating() : 0.0;
        ratingBar.setRating((float) ratingValue);
        tvRating.setText(String.format(Locale.TAIWAN, "%.1f", ratingValue));

        // Meta: 地址或價位
        String meta = (p.getAddress() != null && !p.getAddress().isEmpty())
                ? p.getAddress()
                : (p.getPriceRange() != null ? p.getPriceRange() : "");
        tvMeta.setText(meta);

        // 標籤 Chips
        chipGroupTags.removeAllViews();
        List<String> tags = p.getTagsTop3();
        if (tags != null && !tags.isEmpty()) {
            chipGroupTags.setVisibility(View.VISIBLE);
            for (String tag : tags) {
                Chip chip = new Chip(requireContext());
                chip.setText(tag);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                chip.setChipBackgroundColorResource(R.color.material_dynamic_neutral90);
                chip.setChipStrokeColorResource(android.R.color.darker_gray);
                chip.setChipStrokeWidth(2f);
                chip.setClickable(false);
                chip.setCheckable(false);
                chipGroupTags.addView(chip);
            }
        } else {
            chipGroupTags.setVisibility(View.GONE);
        }
    }

    private void toggleFavoriteStatus() {
        if (currentPlace == null || favoritesStore == null || currentPlace.getId() == null) return;

        isCurrentlyFavorite = !isCurrentlyFavorite;
        if (isCurrentlyFavorite) {
            favoritesStore.add(currentPlace);
            Toast.makeText(getContext(), "已收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoritesStore.removeById(currentPlace.getId());
            Toast.makeText(getContext(), "已取消收藏", Toast.LENGTH_SHORT).show();
        }
        updateHeartButtonUI();
    }

    private void updateHeartButtonUI() {
        if (getContext() == null || btnHeart == null) return;
        if (isCurrentlyFavorite) {
            btnHeart.setText("已收藏");
            btnHeart.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.baseline_favorite_24));
        } else {
            btnHeart.setText("收藏");
            btnHeart.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.outline_favorite_24));
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = ((BottomSheetDialog) d).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    private void openGoogleMaps() {
        if (getContext() == null || currentPlace == null) return;
        String addr = (currentPlace.getAddress() != null && !currentPlace.getAddress().isEmpty())
                ? currentPlace.getAddress() : "台中市西屯區";
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(addr));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(getContext(), "請安裝 Google Maps", Toast.LENGTH_SHORT).show();
        }
    }
}
