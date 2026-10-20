package com.alifabc.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.Locale;

/**
 * Shows the bundled web app (assets/www) in a full-screen WebView.
 * Files are served from a virtual https origin, so fonts, storage and sound effects work like in a browser, with no internet.
 * Android's WebView has no speechSynthesis, so spoken letters and words go through the phone's own TextToSpeech engine.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingText = null;
    private String pendingLang = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tts = new TextToSpeech(getApplicationContext(), status -> {
            synchronized (MainActivity.this) {
                ttsReady = (status == TextToSpeech.SUCCESS) && tts != null;
                if (ttsReady) {
                    tts.setSpeechRate(0.85f);
                    tts.setPitch(1.1f);
                    if (pendingText != null) {
                        doSpeak(pendingText, pendingLang, false);
                    }
                }
                pendingText = null;
                pendingLang = null;
            }
        });

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#EAF3FF"));
        setContentView(webView);

        // Keep the page clear of the system bars (needed when the system draws edge to edge).
        webView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        webView.addJavascriptInterface(new SpeechBridge(), "AndroidTTS");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    /** Called from the page as window.AndroidTTS.speak(text, "ar-SA", queue). */
    private class SpeechBridge {
        @JavascriptInterface
        public void speak(String text, String langTag, boolean queue) {
            synchronized (MainActivity.this) {
                if (tts == null) return;
                if (!ttsReady) {
                    // The engine is still starting: remember the last request and say it once ready.
                    pendingText = text;
                    pendingLang = langTag;
                    return;
                }
                doSpeak(text, langTag, queue);
            }
        }

        @JavascriptInterface
        public void stop() {
            synchronized (MainActivity.this) {
                if (tts != null) tts.stop();
            }
        }
    }

    /** Must be called while holding the lock on MainActivity.this. */
    private void doSpeak(String text, String langTag, boolean queue) {
        Locale wanted = Locale.forLanguageTag(langTag);
        int result = tts.setLanguage(wanted);
        if (result < TextToSpeech.LANG_AVAILABLE) {
            result = tts.setLanguage(new Locale(wanted.getLanguage()));
        }
        if (result < TextToSpeech.LANG_AVAILABLE) {
            // No voice for this language on the phone: tell the page so it can show a hint.
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.evaluateJavascript("window.__ttsMissing&&window.__ttsMissing()", null);
                }
            });
            return;
        }
        tts.speak(text, queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, null,
                "alif-" + System.nanoTime());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    /** Back first closes the open panel/lesson inside the app, then leaves the app. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        webView.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", value -> {
            if (!"\"1\"".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        synchronized (this) {
            if (tts != null) tts.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        synchronized (this) {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
        }
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}
