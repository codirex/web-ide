package org.codirex.webide;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

/**
 * Live view: a WebView for the page served by a dev server on this phone. The WebView is only
 * created when the panel is first shown, and destroyed with the screen.
 */
final class LiveViewPanel {

    private static final String DEFAULT_URL = "http://127.0.0.1:5173";

    private final Context context;
    private final FrameLayout container;
    private final TextInputEditText urlInput;
    private final TextView hint;
    private WebView webView;

    LiveViewPanel(Context context, FrameLayout container, TextInputEditText urlInput,
                  android.view.View reloadButton, TextView hint) {
        this.context = context;
        this.container = container;
        this.urlInput = urlInput;
        this.hint = hint;
        reloadButton.setOnClickListener(v -> load());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load();
                return true;
            }
            return false;
        });
    }

    /** Called when the panel becomes visible. */
    void onShown() {
        if (webView == null) {
            create();
            load();
        }
    }

    void load() {
        if (webView == null) {
            create();
        }
        webView.loadUrl(normalize(String.valueOf(urlInput.getText())));
    }

    void destroy() {
        if (webView != null) {
            container.removeView(webView);
            webView.destroy();
            webView = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void create() {
        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                hint.setVisibility(android.view.View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    hint.setText(context.getString(R.string.live_unreachable,
                            request.getUrl().toString(), error.getDescription()));
                    hint.setVisibility(android.view.View.VISIBLE);
                }
            }
        });
        container.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** "5173" becomes http://127.0.0.1:5173, "localhost:3000" becomes http://localhost:3000. */
    static String normalize(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return DEFAULT_URL;
        }
        if (t.matches("\\d{2,5}")) {
            return "http://127.0.0.1:" + t;
        }
        if (!t.contains("://")) {
            return "http://" + t;
        }
        return t;
    }
}
