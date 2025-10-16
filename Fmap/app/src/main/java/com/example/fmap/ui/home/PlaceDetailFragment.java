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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.data.FavoritesStore;
import com.example.fmap.model.Place;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton; // ✨ 1. 將 Button 改為 MaterialButton
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class PlaceDetailFragment extends BottomSheetDialogFragment {

    private static final String ARG_PLACE_ID = "place_id";

    private FirebaseFirestore db;
    private FavoritesStore favoritesStore;
    private String placeId;
    private Place currentPlace;
    private boolean isCurrentlyFavorite = false;

    // View 變數
    private ImageView imgThumb;
    private TextView tvName, tvRating, tvMeta;
    private RatingBar ratingBar;
    private ChipGroup chipGroupTags;
    private MaterialButton btnNavigate, btnHeart; // ✨ 1. 將 Button 改為 MaterialButton

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
        db = FirebaseFirestore.getInstance();
        if (getContext() != null) {
            // ✨ 2. 核心修改：使用 getInstance() 來獲取單例物件
            favoritesStore = FavoritesStore.getInstance(getContext());
        }

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
            loadPlaceDetails();
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

    private void toggleFavoriteStatus() {
        if (currentPlace == null || favoritesStore == null) return;

        isCurrentlyFavorite = !isCurrentlyFavorite;

        if (isCurrentlyFavorite) {
            favoritesStore.add(currentPlace);
            Toast.makeText(getContext(), "已收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoritesStore.remove(currentPlace.id);
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

    private void loadPlaceDetails() {
        db.collection("stores_summary").document(placeId)
                .get()
                .addOnSuccessListener(this::bindData)
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "讀取資料失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        dismiss();
                    }
                });
    }

    private void bindData(@NonNull DocumentSnapshot doc) {
        if (getContext() == null || !doc.exists()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "找不到該店家資料", Toast.LENGTH_LONG).show();
                dismiss();
            }
            return;
        }

        currentPlace = doc.toObject(Place.class);
        if (currentPlace == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "資料格式錯誤", Toast.LENGTH_LONG).show();
                dismiss();
            }
            return;
        }
        currentPlace.id = doc.getId();

        if(favoritesStore != null) {
            isCurrentlyFavorite = favoritesStore.isFavorite(currentPlace.id);
            updateHeartButtonUI();
        }

        Glide.with(this).load(currentPlace.photoUrl).placeholder(R.color.material_dynamic_neutral90).centerCrop().into(imgThumb);
        tvName.setText(currentPlace.name != null ? currentPlace.name : "未命名店家");
        double ratingValue = currentPlace.rating != null ? currentPlace.rating : 0.0;
        ratingBar.setRating((float) ratingValue);
        tvRating.setText(String.format(Locale.TAIWAN, "%.1f", ratingValue));
        tvMeta.setText((currentPlace.introLine != null && !currentPlace.introLine.isEmpty()) ? currentPlace.introLine : "暫無簡介");

        chipGroupTags.removeAllViews();
        List<String> tags = currentPlace.tags;
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

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    private void openGoogleMaps() {
        if (getContext() == null || currentPlace == null) return;
        String addr = (currentPlace.address != null && !currentPlace.address.isEmpty())
                ? currentPlace.address : "台中市西屯區";
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
