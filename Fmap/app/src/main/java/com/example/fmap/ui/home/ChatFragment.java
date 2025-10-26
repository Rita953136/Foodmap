package com.example.fmap.ui.home;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.fmap.R;

public class ChatFragment extends Fragment {

    private WebView webView;
    private ProgressBar progress;
    private ImageButton btnBack;

    // 換成你 Agent Builder / ngrok 的網址
    private final String chatUrl = "https://unfactored-wittily-karleen.ngrok-free.dev/demo";


    public ChatFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        webView = v.findViewById(R.id.webview);
        progress = v.findViewById(R.id.progress);
        btnBack = v.findViewById(R.id.btn_back);

        setupWebView();
        webView.loadUrl(chatUrl);

        // ✨✨✨【最關鍵的修正】✨✨✨
        // 為返回按鈕設定點擊監聽事件
        btnBack.setOnClickListener(view -> {
            // 檢查 Fragment 是否還附加在 Activity 上，並且確認這個 Activity 是 MainActivity
            if (isAdded() && getActivity() instanceof MainActivity) {
                // 直接呼叫 MainActivity 的公開方法來關閉聊天視窗，而不是自己處理導航
                ((MainActivity) getActivity()).closeChatFragment();
            }
        });

        return v;
    }

    // WebView 的設定 (保持你原有的完整設定)
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // isAdded() 檢查可以避免 Fragment 分離後還嘗試取得 Context 造成的閃退
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "載入失敗：" + error.getDescription(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(newProgress);
            }
        });
    }

    // JSBridge：前端呼叫 window.Android.postEvent(type, json) (保持你原有的完整設定)
    public class AndroidBridge {
        @JavascriptInterface
        public void postEvent(String type, String json) {
            // isAdded() 檢查可以避免 Fragment 分離後還嘗試操作 Activity
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if ("ERROR".equals(type)) {
                    Toast.makeText(requireContext(), "Agent 錯誤：" + json, Toast.LENGTH_SHORT).show();
                } else {
                    // 先不處理推薦事件，只顯示提示
                    Toast.makeText(requireContext(), "收到事件：" + type, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            // 先將 WebView 從父容器中移除，避免記憶體洩漏
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeJavascriptInterface("Android");
            // 清理 WebView 資源
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.clearView();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    // 按返回鍵時回上一頁 (這個方法現在由 MainActivity 統一呼叫)
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true; // 回傳 true，表示 WebView 自己處理了返回事件
        }
        return false; // 回傳 false，表示 WebView 無法返回，讓 MainActivity 關閉聊天視窗
    }
}
