package com.example.fmap.ui.home;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    // 頁面載入完成旗標與待送佇列（頁面未就緒時先暫存）
    private boolean webReady = false;
    private List<String> pendingTags = null;

    // Agent Builder / ngrok 的網址
    private final String chatUrl = "https://unfactored-wittily-karleen.ngrok-free.dev/";

    public ChatFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        webView  = v.findViewById(R.id.webview);
        progress = v.findViewById(R.id.progress);
        btnBack  = v.findViewById(R.id.btn_back);

        setupWebView();
        if (webView != null) {
            webView.loadUrl(chatUrl);
        }

        // 返回按鈕 → 交給 MainActivity 關閉聊天容器
        if (btnBack != null) {
            btnBack.setOnClickListener(view -> {
                if (isAdded() && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).closeChatFragment();
                }
            });
        }
        return v;
    }

    // ====== 提供給 MainActivity 的公開方法：更新可見標籤小卡（前端 UI 用）======
    public void updateVisibleTags(@NonNull Collection<String> tags) {
        if (webView == null) return;
        try {
            JSONArray arr = new JSONArray();
            int i = 0;
            for (String t : tags) {
                if (t == null) continue;
                if (i++ >= 12) break;
                arr.put(t);
            }
            String js = "window.postMessage({type:'TAGS_SET_VISIBLE', tags:" + arr.toString() + "}, '*');";
            runJs(js);
        } catch (Exception ignore) {}
    }

    // ====== 新增：一次把「多選標籤（最終結果）」送進內建頁面（給關抽屜後呼叫）======
    public void applySelectedTags(@NonNull List<String> tags) {
        if (webView == null) return;
        if (!webReady) {
            // 頁面尚未 ready，先暫存，onPageFinished 時會送出
            pendingTags = new ArrayList<>(tags);
            return;
        }
        internalPostTags(tags);
    }

    // （選配）整頁重載並夾帶 query，若你想「進頁就有預設標籤」
    public void reloadWithTags(@NonNull List<String> tags) {
        if (webView == null) return;
        String csv = TextUtils.join(",", tags);
        String url = chatUrl + "?tags=" + Uri.encode(csv);
        webReady = false;      // 重新載入，重置旗標
        pendingTags = null;    // 清掉暫存（進頁會由 /start 讀取 state，或載入完成後再送）
        webView.post(() -> webView.loadUrl(url));
    }

    // ====== WebView 設定（支援 ChatKit + 可見標籤列）=====
    @SuppressLint({"SetJavaScriptEnabled"})
    private void setupWebView() {
        if (webView == null) return;

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

// 不要用「整頁縮放成一個畫面」
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);

// 關掉縮放控制（可選）
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

// 初始比例維持 100%
        webView.setInitialScale(100);
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
                webReady = false;
                if (progress != null) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(5);
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                webReady = true;
                if (progress != null) progress.setVisibility(View.GONE);

                // 若有等待中的標籤 —— 立刻送
                if (pendingTags != null && !pendingTags.isEmpty()) {
                    internalPostTags(pendingTags);
                    pendingTags = null;
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "載入失敗：" + error.getDescription(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (progress != null) {
                    progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                    progress.setProgress(newProgress);
                }
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
            // 確保在 UI 執行緒上操作
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.removeJavascriptInterface("Android");
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        tagRemoveListener = null; // 清理監聽器
        pendingTags = null;
        webReady = false;
        super.onDestroyView();
    }

    // ====== 工具：把 tags 送進頁面（window.postMessage）=====
    private void internalPostTags(@NonNull List<String> tags) {
        JSONArray arr = new JSONArray();
        for (String t : tags) {
            if (t == null) continue;
            arr.put(t);
        }
        String js = "window.postMessage({type:'APP_SELECTED_TAGS', payload:" + arr.toString() + "}, '*');";
        runJs(js);
    }

    private void runJs(@NonNull String js) {
        if (webView == null) return;
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> webView.evaluateJavascript(js, null));
        } else {
            webView.post(() -> webView.evaluateJavascript(js, null));
        }
    }
}
