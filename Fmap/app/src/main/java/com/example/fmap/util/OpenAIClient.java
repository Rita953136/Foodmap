// 檔案路徑: .../app/src/main/java/com/example/fmap/util/OpenAIClient.java

package com.example.fmap.util;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OpenAIClient {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String apiKey;

    // ✨ 1. 新增建構函式，在建立物件時就傳入 API Key
    public OpenAIClient(String apiKey) {
        // 檢查 API 金鑰是否存在，如果不存在就拋出錯誤
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY")) {
            throw new IllegalArgumentException("錯誤：找不到或未設定 API 金鑰。請在 ChatFragment 中設定您的 OpenAI API Key。");
        }
        this.apiKey = apiKey;
    }

    // ✨ 2. 新增 AgentBuilder 內部類別，用於建立帶有上下文的請求
    public class AgentBuilder {
        private final JsonArray messages = new JsonArray();

        public AgentBuilder setSystemMessage(String content) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", content);
            messages.add(systemMessage);
            return this; // 允許鏈式呼叫
        }

        public AgentBuilder addUserMessage(String content) {
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", content);
            messages.add(userMessage);
            return this; // 允許鏈式呼叫
        }

        public JsonArray build() {
            return messages;
        }
    }

    // ✨ 3. 新增 createChatCompletion 方法，接收 AgentBuilder 並使用非同步 Callback
    public void createChatCompletion(@NonNull AgentBuilder agentBuilder, @NonNull Callback callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", "gpt-3.5-turbo");
        payload.add("messages", agentBuilder.build());

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(callback);
    }

    // ✨ 4. 舊的、同步的 ask 方法已被移除，因為它會阻塞主執行緒且已被新方法取代。
}
