// 檔案路徑: C:/Users/rita9/Documents/Foodmap/Fmap/app/src/main/java/com/example/fmap/ui/home/ChatFragment.java
package com.example.fmap.ui.home;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
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

import org.json.JSONArray;

import java.util.Collection;

public class ChatFragment extends Fragment {

    // ====== 讓 Activity 能收到「叉叉刪除某標籤」的回呼 ======
    public interface OnTagRemoveListener {
        void onTagRemove(String tag);
    }
    private OnTagRemoveListener tagRemoveListener;
    public void setOnTagRemoveListener(OnTagRemoveListener l) { this.tagRemoveListener = l; }

    private WebView webView;
    private ProgressBar progress;
    private ImageButton btnBack;

    // 換成你 Agent Builder / ngrok 的網址
    private final String chatUrl = "https://unfactored-wittily-karleen.ngrok-free.dev/demo";

    public ChatFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        webView  = v.findViewById(R.id.webview);
        progress = v.findViewById(R.id.progress);
        btnBack  = v.findViewById(R.id.btn_back);

        setupWebView();
        webView.loadUrl(chatUrl);

        // 返回按鈕 → 交給 MainActivity 關閉聊天容器
        btnBack.setOnClickListener(view -> {
            if (isAdded() && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).closeChatFragment();
            }
        });
        return v;
    }

    // ====== 提供給 MainActivity 的公開方法：更新可見標籤小卡 ======
    public void updateVisibleTags(@NonNull Collection<String> tags) {
        if (webView == null) return;
        try {
            JSONArray arr = new JSONArray();
            int i = 0;
            for (String t : tags) {
                if (t == null) continue;
                if (i++ >= 12) break; // 上限避免爆版
                arr.put(t);
            }
            String js = "window.postMessage({type:'TAGS_SET_VISIBLE', tags:" + arr.toString() + "})";
            webView.evaluateJavascript(js, null);
        } catch (Exception ignore) {}
    }

    // ====== WebView 設定（支援 ChatKit + 可見標籤列）=====
    @SuppressLint({"SetJavaScriptEnabled"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 接受第三方 Cookie（有時需要）
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // JS Bridge：頁面可呼叫 window.Android.xxx(...)
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isAdded()) {
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

    // ====== JS Bridge：頁面 → 原生 ======
    public class AndroidBridge {
        @JavascriptInterface
        public void postEvent(String type, String json) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "收到事件：" + type, Toast.LENGTH_SHORT).show()
            );
        }

        // ★★ 頁面按「×」刪除小卡 → 呼叫 Android.onTagRemove(tag)
        @JavascriptInterface
        public void onTagRemove(String tag) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (tagRemoveListener != null) tagRemoveListener.onTagRemove(tag);
            });
        }
    }

    // ====== 返回鍵交給 WebView 處理（能回上一頁就吃掉事件）=====
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeJavascriptInterface("Android");
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
