// 檔案路徑: app/src/main/java/com/example/fmap/ui/home/PlaceDetailFragment.java
package com.example.fmap.ui.home;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.model.Place;
import com.example.fmap.model.TimeRange;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 店家詳情（本機資料版）
 * - 使用你的 FavoritesStore（SharedPreferences）
 * - 使用你的 StoreRepository（ui.home wrapper -> data.StoresRepository）
 * - 營業時間支援「今天摘要 + 展開/收合」
 */
public class PlaceDetailFragment extends BottomSheetDialogFragment {

    private static final String ARG_PLACE_ID  = "place_id";
    private static final String ARG_PLACE_OBJ = "arg_place_obj";

    // Data
    private FavoritesStore favoritesStore;
    private StoreRepository storeRepo;
    private String placeId;
    private Place currentPlace;
    private boolean isCurrentlyFavorite = false;

    // Views
    private ImageView imgThumb, ivHoursChevron;
    private TextView tvName, tvRating, tvMeta, tvPrice, tvAddress, tvPhone, tvHoursSummary;
    private RatingBar ratingBar;
    private ChipGroup chipGroupTags, chipGroupMenu;
    private MaterialButton btnNavigate, btnHeart;
    private LinearLayout rowAddress, rowPhone, rowHoursHeader, hoursContainer;

    public static PlaceDetailFragment newInstance(@Nullable String placeId, @Nullable Place fallbackPlace) {
        PlaceDetailFragment fragment = new PlaceDetailFragment();
        Bundle args = new Bundle();
        if (placeId != null) args.putString(ARG_PLACE_ID, placeId);
        if (fallbackPlace != null) args.putSerializable(ARG_PLACE_OBJ, fallbackPlace);
        fragment.setArguments(args);
        return fragment;
    }
    // 保留舊呼叫方式 (相容舊程式)
    public static PlaceDetailFragment newInstance(String placeId) {
        return newInstance(placeId, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favoritesStore = FavoritesStore.getInstance(requireContext());
        storeRepo = new StoreRepository(requireActivity().getApplication());
        if (getArguments() != null) {
            placeId = getArguments().getString(ARG_PLACE_ID);
            Object s = getArguments().getSerializable(ARG_PLACE_OBJ);
            if (s instanceof Place) currentPlace = (Place) s; // 先存起來當備援
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
        initViews(view);
        setupClicks();

        // 先顯示備援（如果有帶 Place 進來，讓 UI 先有畫面）
        if (currentPlace != null) {
            bindPlaceToViews(currentPlace);
            if (currentPlace.getId() != null) {
                isCurrentlyFavorite = favoritesStore.contains(currentPlace.getId());
                updateHeartButtonUI();
            }
        }

        // 沒有 id 就只用備援；有 id 才去查資料庫
        if (placeId == null || placeId.isEmpty()) return;

        // 確保已初始化，且等 DB ready 才查
        storeRepo.initFromAssets(requireContext());
        storeRepo.getDbReady().observe(getViewLifecycleOwner(), ready -> {
            if (Boolean.TRUE.equals(ready)) {
                loadPlaceDetailsLocal();
            }
        });
    }

    private void initViews(@NonNull View v) {
        imgThumb = v.findViewById(R.id.imgThumb);
        tvName = v.findViewById(R.id.tvName);
        tvRating = v.findViewById(R.id.tvRating);
        ratingBar = v.findViewById(R.id.ratingBar);
        chipGroupTags = v.findViewById(R.id.chip_group_tags);
        btnNavigate = v.findViewById(R.id.btnNavigate);
        btnHeart = v.findViewById(R.id.btnHeart);
        tvMeta = v.findViewById(R.id.tvMeta);

        tvPrice = v.findViewById(R.id.tvPrice);
        rowAddress = v.findViewById(R.id.rowAddress);
        tvAddress = v.findViewById(R.id.tvAddress);
        rowPhone = v.findViewById(R.id.rowPhone);
        tvPhone = v.findViewById(R.id.tvPhone);

        rowHoursHeader = v.findViewById(R.id.rowHoursHeader);
        tvHoursSummary = v.findViewById(R.id.tvHoursSummary);
        ivHoursChevron = v.findViewById(R.id.ivHoursChevron);
        hoursContainer = v.findViewById(R.id.hoursContainer);
        chipGroupMenu = v.findViewById(R.id.chip_group_menu);
        if (chipGroupMenu != null) chipGroupMenu.setVisibility(View.GONE);
    }

    private void setupClicks() {
        btnNavigate.setOnClickListener(v -> openGoogleMaps());
        btnHeart.setOnClickListener(v -> toggleFavoriteStatus());
        rowHoursHeader.setOnClickListener(v -> toggleHoursVisibility());
        rowPhone.setOnClickListener(v -> dialPhoneNumber());
    }

    // ------- 讀取本機資料 -------
    private void loadPlaceDetailsLocal() {
        storeRepo.getByIds(Collections.singletonList(placeId))
                .observe(getViewLifecycleOwner(), entities -> {
                    if (entities == null || entities.isEmpty()) {
                        if (currentPlace != null) {
                            return;
                        } else {
                            Toast.makeText(getContext(), "找不到該店家資料", Toast.LENGTH_LONG).show();
                            dismiss();
                            return;
                        }
                    }

                    com.example.fmap.data.local.StoreEntity e = entities.get(0);
                    Place mapped = com.example.fmap.data.local.StoreMappers.toPlace(e);
                    if (mapped == null) {
                        if (currentPlace != null) return;
                        Toast.makeText(getContext(), "資料格式錯誤", Toast.LENGTH_LONG).show();
                        dismiss();
                        return;
                    }

                    currentPlace = mapped;
                    if (currentPlace.getId() != null) {
                        isCurrentlyFavorite = favoritesStore.contains(currentPlace.getId());
                    }
                    updateHeartButtonUI();
                    bindPlaceToViews(currentPlace);
                });
    }

    private void bindPlaceToViews(@NonNull Place p) {
        // 圖片：assets 或 http(s)
        Glide.with(this)
                .load(p.getCoverImageFullPath())
                .placeholder(new ColorDrawable(Color.parseColor("#E0E0E0")))
                .centerCrop()
                .into(imgThumb);

        // 名稱
        tvName.setText(!TextUtils.isEmpty(p.getName()) ? p.getName() : "未命名店家");

        // 評分（沒有就隱藏整塊）
        Double ratingValue = p.getRating();
        View ratingLayout = requireView().findViewById(R.id.layoutRating);
        if (ratingValue == null) {
            ratingLayout.setVisibility(View.GONE);
        } else {
            ratingLayout.setVisibility(View.VISIBLE);
            ratingBar.setRating(ratingValue.floatValue());
            tvRating.setText(String.format(Locale.TAIWAN, "%.1f", ratingValue));
        }

        // Meta：用標籤組成一句（沒有就隱藏）
        String metaText = (p.getTagsTop3() != null && !p.getTagsTop3().isEmpty())
                ? TextUtils.join("・", p.getTagsTop3()) : "";
        tvMeta.setText(metaText);
        tvMeta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);

        // 價位
        String priceText = p.getPriceRange();
        tvPrice.setText(!TextUtils.isEmpty(priceText) ? priceText : "");
        tvPrice.setVisibility(TextUtils.isEmpty(priceText) ? View.GONE : View.VISIBLE);

        // 地址
        String addressText = p.getAddress();
        if (!TextUtils.isEmpty(addressText)) {
            tvAddress.setText(addressText);
            rowAddress.setVisibility(View.VISIBLE);
        } else {
            rowAddress.setVisibility(View.GONE);
        }

        // 電話
        String phoneText = p.getPhone();
        if (!TextUtils.isEmpty(phoneText)) {
            tvPhone.setText(phoneText);
            rowPhone.setVisibility(View.VISIBLE);
        } else {
            rowPhone.setVisibility(View.GONE);
        }

        // 標籤 & 菜單
        // 只顯示標籤，不顯示菜單
        bindChips(chipGroupTags, p.getTagsTop3(), false);
        if (chipGroupMenu != null) {
            chipGroupMenu.removeAllViews();
            chipGroupMenu.setVisibility(View.GONE);
        }

        // 營業時間
        bindBusinessHours(p);
    }

    private void bindChips(ChipGroup chipGroup, List<String> items, boolean isMenu) {
        chipGroup.removeAllViews();
        if (items == null || items.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
            return;
        }
        chipGroup.setVisibility(View.VISIBLE);
        for (String t : items) {
            Chip chip = new Chip(requireContext());
            chip.setText(t);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            if (isMenu) {
                chip.setChipBackgroundColorResource(android.R.color.holo_purple);
                chip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
            } else {
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setChipBackgroundColorResource(R.color.material_dynamic_neutral90);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            }
            chip.setClickable(false);
            chip.setCheckable(false);
            chipGroup.addView(chip);
        }
    }

    // ====== 營業時間（摘要 + 展開明細） ======
    private static final String[] WEEK_CN = {"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};

    private void bindBusinessHours(Place p) {
        Map<String, List<TimeRange>> map = p.getBusinessHours();
        if (map == null || map.isEmpty()) {
            rowHoursHeader.setVisibility(View.GONE);
            hoursContainer.setVisibility(View.GONE);
            return;
        }
        rowHoursHeader.setVisibility(View.VISIBLE);

        // 今日摘要（以中文鍵）
        Calendar cal = Calendar.getInstance();
        int idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0 … Sun=6
        String todayKey = WEEK_CN[idx];
        List<TimeRange> today = findByAliases(map, todayKey);
        tvHoursSummary.setText((today == null || today.isEmpty())
                ? "今天：公休"
                : "今天：" + joinRangesAny(today));

        // 明細列表
        hoursContainer.removeAllViews();
        for (String k : WEEK_CN) {
            List<TimeRange> ranges = findByAliases(map, k);
            TextView tv = new TextView(getContext());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            tv.setPadding(0, dp(4), 0, dp(4));
            tv.setText((ranges == null || ranges.isEmpty()) ? (k + "　公休") : (k + "　" + joinRangesAny(ranges)));
            hoursContainer.addView(tv);
        }
    }

    private List<TimeRange> findByAliases(
            Map<String, List<TimeRange>> map, String keyCn) {

        String[] aliases = aliasesOf(keyCn);
        for (String a : aliases) {
            if (map.containsKey(a)) return map.get(a);
            if (map.containsKey(a.toLowerCase())) return map.get(a.toLowerCase());
            if (map.containsKey(a.toUpperCase())) return map.get(a.toUpperCase());
            String noSpace = a.replace(" ", "");
            if (map.containsKey(noSpace)) return map.get(noSpace);
        }
        return null;
    }

    private String[] aliasesOf(String cn) {
        switch (cn) {
            case "星期一": return new String[]{"星期一","週一","周一","Mon","MON","Monday"};
            case "星期二": return new String[]{"星期二","週二","周二","Tue","TUE","Tuesday"};
            case "星期三": return new String[]{"星期三","週三","周三","Wed","WED","Wednesday"};
            case "星期四": return new String[]{"星期四","週四","周四","Thu","THU","Thursday"};
            case "星期五": return new String[]{"星期五","週五","周五","Fri","FRI","Friday"};
            case "星期六": return new String[]{"星期六","週六","周六","Sat","SAT","Saturday"};
            default:       return new String[]{"星期日","週日","周日","Sun","SUN","Sunday"};
        }
    }

    /** 將一日內多段時間組成字串（兼容任何 TimeRange 結構：有 getter 或只有欄位都可） */
    private String joinRangesAny(List<?> ranges) {
        List<String> parts = new ArrayList<>();
        if (ranges != null) {
            for (Object r : ranges) {
                String open  = getTimeFieldAny(r, "getOpen",  "open");
                String close = getTimeFieldAny(r, "getClose", "close");
                if (!TextUtils.isEmpty(open) && !TextUtils.isEmpty(close)) {
                    parts.add(open + "–" + close);
                }
            }
        }
        return parts.isEmpty() ? "公休" : TextUtils.join("、", parts);
    }

    /** 先找 getter，沒有就讀欄位（通用版） */
    private String getTimeFieldAny(Object r, String getter, String fieldName) {
        if (r == null) return "";
        try {
            Method m = r.getClass().getMethod(getter);
            Object v = m.invoke(r);
            return v == null ? "" : v.toString().trim();
        } catch (Exception ignore) {
            try {
                Field f = r.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(r);
                return v == null ? "" : v.toString().trim();
            } catch (Exception e) {
                return "";
            }
        }
    }

    private void toggleHoursVisibility() {
        boolean expand = hoursContainer.getVisibility() != View.VISIBLE;
        hoursContainer.setVisibility(expand ? View.VISIBLE : View.GONE);
        ivHoursChevron.animate().rotation(expand ? 180f : 0f).setDuration(150).start();

        if (expand) {
            // 展開時，若外層是 ScrollView，就捲到這個位置
            View parent = (View) rowHoursHeader.getParent();
            while (parent != null && !(parent instanceof ScrollView)) {
                View p = (View) parent.getParent();
                if (p == parent) break;
                parent = p;
            }
            if (parent instanceof ScrollView) {
                ((ScrollView) parent).smoothScrollTo(0, rowHoursHeader.getTop());
            }
        }
        rowHoursHeader.setContentDescription(expand ? "收合營業時間" : "展開營業時間");
    }

    //（下列兩個方法保留給可能的他處使用；已改用 joinRangesAny，不會衝突）
    private String buildTodaySummary(Map<String, List<TimeRange>> map) {
        if (map == null || map.isEmpty()) return "無營業時間資料";
        String today = dayZh(Calendar.getInstance());
        List<TimeRange> ranges = map.get(today);
        if (ranges == null || ranges.isEmpty()) return "今天：公休";
        return "今天：" + joinRangesAny(ranges);
    }

    private String buildDayLine(String day, Map<String, List<TimeRange>> map) {
        if (map == null) return null;
        List<TimeRange> ranges = map.get(day);
        if (ranges == null || ranges.isEmpty()) return day + "　公休";
        return day + "　" + joinRangesAny(ranges);
    }

    private String[] dayOrderZh() {
        return new String[]{"週一","週二","週三","週四","週五","週六","週日"};
    }

    private String dayZh(Calendar cal) {
        int d = cal.get(Calendar.DAY_OF_WEEK);
        switch (d) {
            case Calendar.MONDAY: return "週一";
            case Calendar.TUESDAY: return "週二";
            case Calendar.WEDNESDAY: return "週三";
            case Calendar.THURSDAY: return "週四";
            case Calendar.FRIDAY: return "週五";
            case Calendar.SATURDAY: return "週六";
            default: return "週日";
        }
    }

    private int dp(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    // --- 電話 ---
    private void dialPhoneNumber() {
        if (currentPlace == null || TextUtils.isEmpty(currentPlace.getPhone())) return;
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + currentPlace.getPhone()));
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), "沒有可以撥打電話的應用程式", Toast.LENGTH_SHORT).show();
        }
    }

    // --- 收藏 ---
    private void toggleFavoriteStatus() {
        if (currentPlace == null || currentPlace.getId() == null) return;
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
        if (btnHeart == null) return;
        if (isCurrentlyFavorite) {
            btnHeart.setText("已收藏");
            btnHeart.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.baseline_favorite_24));
        } else {
            btnHeart.setText("收藏");
            btnHeart.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.outline_favorite_24));
        }
    }

    // --- Google Maps 導航 ---
    private void openGoogleMaps() {
        if (getContext() == null || currentPlace == null) return;
        String query;
        if (currentPlace.getLat() != null && currentPlace.getLng() != null && currentPlace.getLat() != 0) {
            query = currentPlace.getLat() + "," + currentPlace.getLng();
        } else {
            query = !TextUtils.isEmpty(currentPlace.getAddress()) ? currentPlace.getAddress() : "台中市";
        }
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(getContext(), "請安裝 Google Maps", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}
