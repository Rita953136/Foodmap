package com.example.fmap.ui.home.chat;
import com.example.fmap.BuildConfig;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fmap.R;
import com.example.fmap.ui.home.HomeViewModel;
import com.example.fmap.ui.home.MainActivity;
import com.example.fmap.util.OpenAIClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChatFragment extends Fragment {
    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnSend, btnClose;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HomeViewModel homeViewModel;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OpenAIClient openAIClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        openAIClient = new OpenAIClient(BuildConfig.OPENAI_API_KEY);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvChat = view.findViewById(R.id.rv_chat_messages);
        etInput = view.findViewById(R.id.et_chat_input);
        btnSend = view.findViewById(R.id.btn_send_chat);
        btnClose = view.findViewById(R.id.btn_close_chat);

        adapter = new ChatAdapter(messages);
        // ✨ 新增：設定 LayoutManager
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(adapter);

        if (messages.isEmpty()) {
            addMessage("你好，我是你的專屬美食顧問，我可以根據你目前看到的店家為你推薦，請問想找什麼樣的餐廳呢？", ChatMessage.Sender.AI);
        }

        btnSend.setOnClickListener(v -> sendMessage());
        btnClose.setOnClickListener(v -> closeChat());
    }

    // ✨ 3. ✨ 升級 sendMessage 方法，改用 AgentBuilder
    // ✨ 3. ✨ 升級 sendMessage 方法，改用 AgentBuilder
    private void sendMessage() {
        String userQuestion = etInput.getText().toString().trim();
        if (userQuestion.isEmpty()) return;

        addMessage(userQuestion, ChatMessage.Sender.USER);
        etInput.setText("");

        // ✨【核心修改】✨
        // 不要立即從 homeViewModel.getCurrentPlaces().getValue() 取值，
        // 因為此時非同步載入可能還沒完成。
        // 改為從 LiveData 中獲取當前的店家列表。
        // ✨ FIX: Change the variable type to your custom Place model
        List<com.example.fmap.model.Place> currentPlaces = homeViewModel.getCurrentPlaces().getValue();
        // 檢查店家列表是否存在且不為空
        if (currentPlaces == null || currentPlaces.isEmpty()) {
            // 如果列表為空，直接給予提示，不呼叫 AI
            addMessage("抱歉，目前還沒有可供推薦的店家資訊。請稍等資料載入或返回地圖篩選店家。", ChatMessage.Sender.AI);
            return; // 提前結束，不執行後續 AI 呼叫
        }

        // --- 只有在確定有資料後，才執行 AI 呼叫 ---

        addMessage("AI 思考中...", ChatMessage.Sender.AI);

        // 在背景執行緒中執行網路請求
        executor.execute(() -> {
            // 將從 LiveData 獲取到的店家列表轉換為 JSON 字串
            String placesJson = gson.toJson(currentPlaces);

            // 使用 AgentBuilder 建立一個包含上下文(店家列表)的請求
            // ✨ 恢復強限制的系統指令，讓 AI 只根據我們提供的資料回答
            OpenAIClient.AgentBuilder agentBuilder = openAIClient.new AgentBuilder()
                    .setSystemMessage("你是「Foodmap」App 的美食顧問。請根據以下JSON格式的店家列表來回答使用者的問題。" +
                            "你的回答必須簡潔、友善，並且『絕對』只能從我提供的資料中選擇，不許自己編造任何清單中不存在的店家或資訊。" +
                            "請用台灣人習慣的繁體中文和親切口氣來回答。\n店家列表：" + placesJson)
                    .addUserMessage(userQuestion);

            // 使用 Callback 方式發送請求，處理非同步回應 (這部分邏輯不變)
            openAIClient.createChatCompletion(agentBuilder, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    handler.post(() -> {
                        removeTypingIndicator();
                        addMessage("抱歉，網路有點不穩，請稍後再試一次。", ChatMessage.Sender.AI);
                        Toast.makeText(getContext(), "AI 連線失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        handler.post(() -> {
                            removeTypingIndicator();
                            addMessage("抱歉，我好像有點短路了，可以再問一次嗎？", ChatMessage.Sender.AI);
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
                    String aiReply = jsonObject.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .get("message").getAsJsonObject()
                            .get("content").getAsString();

                    handler.post(() -> {
                        removeTypingIndicator();
                        addMessage(aiReply, ChatMessage.Sender.AI);
                    });
                }
            });
        });
    }

    // 新增一個輔助方法，用於移除「思考中...」的訊息
    private void removeTypingIndicator() {
        if (!messages.isEmpty() && "AI 思考中...".equals(messages.get(messages.size() - 1).text)) {
            messages.remove(messages.size() - 1);
            adapter.notifyItemRemoved(messages.size());
        }
    }

    private void addMessage(String text, ChatMessage.Sender sender) {
        messages.add(new ChatMessage(text, sender));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
    }

    private void closeChat() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).closeChatFragment();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
