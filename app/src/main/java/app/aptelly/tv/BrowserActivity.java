package app.aptelly.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;


import java.util.Locale;

public final class BrowserActivity extends Activity {
    public static final String EXTRA_URL = "url";
    private static final String DEFAULT_URL = "https://www.google.com/";

    private WebView webView;
    private EditText address;
    private ProgressBar progress;
    private LinearLayout browserChrome;
    private FrameLayout fullScreenHost;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    public static void open(Activity activity, String url) {
        Intent intent = new Intent(activity, BrowserActivity.class);
        intent.putExtra(EXTRA_URL, url);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(buildScreen());
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.browser_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        hideSystemBars();
        String requested = getIntent().getStringExtra(EXTRA_URL);
        webView.loadUrl(normalizeUrl(requested));
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setDownloadListener(null);
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private View buildScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);

        browserChrome = new LinearLayout(this);
        browserChrome.setOrientation(LinearLayout.VERTICAL);
        browserChrome.setBackgroundColor(Color.rgb(4, 8, 16));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), dp(12), dp(18), dp(10));
        GradientDrawable toolbarBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.rgb(8, 27, 42),
                        Color.rgb(20, 27, 62),
                        Color.rgb(27, 20, 59)
                }
        );
        toolbarBackground.setStroke(dp(1), Color.argb(46, 221, 238, 255));
        toolbar.setBackground(toolbarBackground);

        Button close = button(getString(R.string.back));
        close.setOnClickListener(view -> finish());
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(104), dp(48)));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.argb(150, 255, 255, 255));
        address.setHint(R.string.browser_address_hint);
        address.setTextSize(14);
        address.setPadding(dp(18), 0, dp(18), 0);
        address.setSelectAllOnFocus(true);
        address.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI
        );
        GradientDrawable addressBackground = rounded(
                Color.argb(145, 7, 14, 27),
                Color.argb(72, 174, 224, 255),
                12
        );
        address.setBackground(addressBackground);
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                webView.loadUrl(normalizeUrl(address.getText().toString()));
                webView.requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        addressParams.leftMargin = dp(12);
        toolbar.addView(address, addressParams);

        Button go = button(getString(R.string.go));
        go.setOnClickListener(view -> {
            webView.loadUrl(normalizeUrl(address.getText().toString()));
            webView.requestFocus();
        });
        LinearLayout.LayoutParams goParams =
                new LinearLayout.LayoutParams(dp(90), dp(48));
        goParams.leftMargin = dp(10);
        toolbar.addView(go, goParams);

        Button reload = button(getString(R.string.reload));
        reload.setOnClickListener(view -> webView.reload());
        LinearLayout.LayoutParams reloadParams =
                new LinearLayout.LayoutParams(dp(104), dp(48));
        reloadParams.leftMargin = dp(10);
        toolbar.addView(reload, reloadParams);
        browserChrome.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70)
        ));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        browserChrome.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        webView = new WebView(this);
        configureWebView(webView);
        browserChrome.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        screen.addView(browserChrome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        fullScreenHost = new FrameLayout(this);
        fullScreenHost.setBackgroundColor(Color.BLACK);
        fullScreenHost.setVisibility(View.GONE);
        screen.addView(fullScreenHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return screen;
    }

    private void configureWebView(WebView value) {
        WebSettings settings = value.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        value.setFocusable(true);
        value.setFocusableInTouchMode(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(value, true);
        value.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return handleSpecialUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleSpecialUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                address.setText(url);
                view.requestFocus();
            }
        });
        value.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (view.getUrl() != null) {
                    address.setText(view.getUrl());
                }
            }

            @Override
            public void onShowCustomView(
                    View view,
                    WebChromeClient.CustomViewCallback callback
            ) {
                showCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });
        value.setDownloadListener(downloadListener());
        value.requestFocus();
    }

    private void showCustomView(
            View view,
            WebChromeClient.CustomViewCallback callback
    ) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        browserChrome.setVisibility(View.GONE);
        fullScreenHost.removeAllViews();
        fullScreenHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        fullScreenHost.setVisibility(View.VISIBLE);
        view.requestFocus();
        hideSystemBars();
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        fullScreenHost.removeView(customView);
        fullScreenHost.setVisibility(View.GONE);
        browserChrome.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        customView = null;
        customViewCallback = null;
        webView.requestFocus();
    }

    private DownloadListener downloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
            String lowerMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
            String lowerDisposition = contentDisposition == null
                    ? ""
                    : contentDisposition.toLowerCase(Locale.ROOT);
            boolean apk = lowerUrl.contains(".apk")
                    || lowerMime.contains("android.package-archive")
                    || lowerDisposition.contains(".apk");
            if (!apk) {
                Toast.makeText(this, R.string.browser_non_apk, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(
                    this,
                    R.string.install_source_unverified,
                    Toast.LENGTH_LONG
            ).show();
        };
    }

    private boolean handleSpecialUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)) {
            return false;
        }
        Toast.makeText(this, R.string.install_source_unverified, Toast.LENGTH_LONG).show();
        return true;
    }

    private String normalizeUrl(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) {
            return DEFAULT_URL;
        }
        if (!candidate.contains("://")) {
            return "https://" + candidate;
        }
        return candidate;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setFocusable(true);
        button.setBackground(buttonBackground());
        button.setOnFocusChangeListener((view, focused) -> {
            button.setTextColor(focused ? Color.rgb(12, 24, 38) : Color.WHITE);
            view.animate()
                    .scaleX(focused ? 1.08f : 1f)
                    .scaleY(focused ? 1.08f : 1f)
                    .translationZ(focused ? dp(10) : 0)
                    .setDuration(120)
                    .start();
        });
        return button;
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_focused},
                rounded(Color.rgb(228, 247, 255), Color.WHITE, 24)
        );
        states.addState(
                new int[]{},
                rounded(
                        Color.argb(62, 255, 255, 255),
                        Color.argb(74, 220, 239, 255),
                        24
                )
        );
        return states;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller =
                    getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
