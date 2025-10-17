package com.example.fmap.util; // 確保 package 名稱正確

import com.example.fmap.BuildConfig; // ✨ 1. 導入自動生成的 BuildConfig
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIClient {
    public static String ask(String prompt) {
        // ✨ 2. 直接從 BuildConfig 取得 API 金鑰，安全又方便！
        String apiKey = BuildConfig.OPENAI_API_KEY;

        // 檢查 API 金鑰是否存在，如果不存在就回傳錯誤訊息
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("null")) {
            return "錯誤：找不到 API 金鑰。請確認 local.properties 和 build.gradle 的設定。";
        }

        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject json = new JSONObject();
            json.put("model", "gpt-3.5-turbo"); // 你可以換成你想用的模型，例如 "gpt-4"
            json.put("messages", messages);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .post(body)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject responseJson = new JSONObject(response.body().string());
                    // 安全地解析 JSON，避免崩潰
                    if (responseJson.has("choices") && responseJson.getJSONArray("choices").length() > 0) {
                        return responseJson
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                    } else {
                        return "錯誤：AI 的回應中沒有有效的 'choices'。";
                    }
                } else {
                    // 如果請求失敗，回傳詳細的錯誤訊息，方便除錯
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    return "錯誤：" + response.code() + " " + response.message() + "\n" + errorBody;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "錯誤：無法取得回應，發生例外狀況：" + e.getMessage();
        }
    }
}
