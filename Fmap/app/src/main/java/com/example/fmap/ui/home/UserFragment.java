package com.example.fmap.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.fmap.R;

public class UserFragment extends Fragment {

    private SharedPreferences prefs;
    private TextView tvName, tvHandle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        prefs = requireContext().getSharedPreferences("user_profile", Context.MODE_PRIVATE);

        tvName   = v.findViewById(R.id.tvName);
        tvHandle = v.findViewById(R.id.tvHandle);
        ImageButton btnEdit = v.findViewById(R.id.btnEditName);

        // 載入假資料（或先前儲存的名稱）
        String name   = prefs.getString("display_name", "使用者");
        String handle = prefs.getString("handle", "@user");
        tvName.setText(name);
        tvHandle.setText(handle);

        // 編輯名稱
        btnEdit.setOnClickListener(view -> showEditNameDialog());
    }

    private void showEditNameDialog() {
        final android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(tvName.getText());

        new AlertDialog.Builder(requireContext())
                .setTitle("編輯名稱")
                .setView(et)
                .setPositiveButton("儲存", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) name = "使用者";
                    String handle = "@" + name.replaceAll("\\s+", "").toLowerCase();

                    prefs.edit().putString("display_name", name)
                            .putString("handle", handle)
                            .apply();

                    tvName.setText(name);
                    tvHandle.setText(handle);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
