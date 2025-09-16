package com.example.fmap.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.fmap.R;
import com.example.fmap.data.FavoritesStore;

import java.util.ArrayList;

public class PlaceDetailActivity extends AppCompatActivity {

    private FavoritesStore store;
    private String id, name, thumb;
    private Double rating;
    private Integer distance, price;
    private ArrayList<String> tags;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_detail); // 確認這個 layout 存在

        store = new FavoritesStore(this);

        ImageView img = findViewById(R.id.imgHeader);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvTags = findViewById(R.id.tvTags);
        ImageButton btnClose = findViewById(R.id.btnClose); // ✅ 叉叉

        // 取 extras（都做 null 安全）
        id       = getIntent().getStringExtra("id");
        name     = getIntent().getStringExtra("name");
        thumb    = getIntent().getStringExtra("thumb");
        try { rating   = (Double)  getIntent().getSerializableExtra("rating"); }   catch (Exception ignored) {}
        try { distance = (Integer) getIntent().getSerializableExtra("distance"); } catch (Exception ignored) {}
        try { price    = (Integer) getIntent().getSerializableExtra("price"); }    catch (Exception ignored) {}
        tags = getIntent().getStringArrayListExtra("tags");
        if (tags == null) tags = new ArrayList<>();

        tvName.setText(name == null ? "" : name);

        String priceStr = (price != null && price > 0)
                ? new String(new char[price]).replace("\0", "$") : "-";
        String distStr  = (distance != null && distance > 0) ? (" · " + distance + "m") : "";
        String rateStr  = (rating != null && rating >= 0) ? String.valueOf(rating) : "-";
        tvMeta.setText("★ " + rateStr + " · " + priceStr + distStr);

        if (!tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(5, tags.size()); i++) {
                if (i > 0) sb.append("  •  ");
                sb.append(tags.get(i));
            }
            tvTags.setText(sb.toString());
        }

        if (thumb != null && !thumb.isEmpty()) Glide.with(this).load(thumb).into(img);


        btnClose.setOnClickListener(v -> {
            if (id != null) store.remove(id);

            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("goto_tab", "favorite");
            startActivity(i);

            finish();
        });
    }
}
