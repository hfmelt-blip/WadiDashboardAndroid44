package com.wadi.dashboard;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private class NetworkBridge {
        @JavascriptInterface
        public void fetchUrl(final String url, final String callbackId) {
            new Thread(new Runnable() {
                @Override public void run() {
                    HttpURLConnection connection = null;
                    String result = null;
                    try {
                        if (!url.startsWith("https://")) throw new Exception("HTTPS only");
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setConnectTimeout(12000);
                        connection.setReadTimeout(12000);
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("User-Agent", "WadiDashboard/1.0 Android");
                        int status = connection.getResponseCode();
                        InputStream stream = status >= 200 && status < 300
                                ? connection.getInputStream() : connection.getErrorStream();
                        if (stream != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                            StringBuilder body = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) body.append(line);
                            reader.close();
                            if (status >= 200 && status < 300) result = body.toString();
                        }
                    } catch (Exception ignored) {
                        result = null;
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    final String response = result;
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                            if (webView == null) return;
                            String safe = response == null ? "null" : org.json.JSONObject.quote(response);
                            webView.evaluateJavascript("window.__wadiNativeResult(" +
                                    org.json.JSONObject.quote(callbackId) + "," + safe + ")", null);
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF101923);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.addJavascriptInterface(new NetworkBridge(), "WadiNetwork");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
