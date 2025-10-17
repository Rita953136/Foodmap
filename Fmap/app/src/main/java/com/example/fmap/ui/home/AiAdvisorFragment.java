package com.example.fmap.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.fmap.model.Place;
import com.example.fmap.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class AiAdvisorFragment extends BottomSheetDialogFragment {

    private ProgressBar loadingIndicator;
    private HomeViewModel homeViewModel;
    private EditText userInput;
    private Button sendButton;
    private TextView responseText;

    // 提供一個靜態工廠方法來建立 Fragment 實例
    public static AiAdvisorFragment newInstance() {
        return new AiAdvisorFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 取得與 MainActivity 共享的 ViewModel 實例
        FragmentActivity activity = getActivity();
        if (activity != null) {
            homeViewModel = new ViewModelProvider(activity).get(HomeViewModel.class);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用我們剛剛建立的 layout 檔案
        return inflater.inflate(R.layout.fragment_ai_advisor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 綁定 View
        userInput = view.findViewById(R.id.userInput);
        sendButton = view.findViewById(R.id.sendButton);
        responseText = view.findViewById(R.id.responseText);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);

        // 設定按鈕點擊事件
        sendButton.setOnClickListener(v -> {
            String question = userInput.getText().toString();
            if (question.trim().isEmpty() || homeViewModel == null) {
                return;
            }
            List<Place> currentPlaces = homeViewModel.getPlaces().getValue();
            if (currentPlaces != null) {
                // ✨ 核心：呼叫 ViewModel 的方法，將邏輯完全交給 ViewModel 處理
                homeViewModel.getAiRecommendation(question, currentPlaces);
            }
        });

        // ✨ 【關鍵修正】呼叫 observeViewModel()，開始監聽 ViewModel 的變化
        observeViewModel();
    }

    private void observeViewModel() {
        if (homeViewModel == null) return;

        // 觀察 AI 是否正在載入中
        homeViewModel.isAiLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                loadingIndicator.setVisibility(View.VISIBLE);
                sendButton.setEnabled(false);
                // 這裡會自動更新文字，所以點擊事件裡不需要再寫
                responseText.setText("AI 思考中...");
            } else {
                loadingIndicator.setVisibility(View.GONE);
                sendButton.setEnabled(true);
            }
        });

        // 觀察 AI 的回應
        homeViewModel.aiResponse.observe(getViewLifecycleOwner(), response -> {
            responseText.setText(response);
        });
    }
}
