package com.wadi.dashboard;

import android.app.Activity;
import android.os.Build;
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
import java.net.Socket;
import java.io.IOException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Android 4.4 can leave TLS 1.2 disabled unless it is explicitly enabled.
    private static class TLS12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        TLS12SocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }
        private Socket enable(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.2", "TLSv1.1", "TLSv1"});
            }
            return socket;
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException { return enable(delegate.createSocket(s, host, port, autoClose)); }
        @Override public Socket createSocket(String host, int port) throws IOException { return enable(delegate.createSocket(host, port)); }
        @Override public Socket createSocket(String host, int port, java.net.InetAddress local, int localPort) throws IOException { return enable(delegate.createSocket(host, port, local, localPort)); }
        @Override public Socket createSocket(java.net.InetAddress host, int port) throws IOException { return enable(delegate.createSocket(host, port)); }
        @Override public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress local, int localPort) throws IOException { return enable(delegate.createSocket(address, port, local, localPort)); }
    }

    private class NetworkBridge {
        @JavascriptInterface
        public void fetchUrl(final String url, final String callbackId) {
            new Thread(new Runnable() {
                @Override public void run() {
                    HttpURLConnection connection = null;
                    String result = null;
                    String error = null;
                    try {
                        if (!url.startsWith("https://")) throw new Exception("HTTPS only");
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        if (connection instanceof HttpsURLConnection && Build.VERSION.SDK_INT <= 20) {
                            ((HttpsURLConnection) connection).setSSLSocketFactory(
                                new TLS12SocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()));
                        }
                        connection.setConnectTimeout(20000);
                        connection.setReadTimeout(20000);
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("User-Agent", "WadiDashboard/1.1 Android");
                        connection.setRequestProperty("Accept", "application/json,*/*");
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
                            else error = "HTTP " + status;
                        } else {
                            error = "HTTP " + status;
                        }
                    } catch (Exception e) {
                        error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    final String response = result;
                    final String errorMessage = error;
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                            if (webView == null) return;
                            String safe = response == null ? "null" : org.json.JSONObject.quote(response);
                            String err = errorMessage == null ? "null" : org.json.JSONObject.quote(errorMessage);
                            webView.evaluateJavascript("window.__wadiNativeResult(" +
                                    org.json.JSONObject.quote(callbackId) + "," + safe + "," + err + ")", null);
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
