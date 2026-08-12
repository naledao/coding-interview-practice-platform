package xyz.kangnasi.interviewpractice;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class WebPageActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST_CODE = 1201;
    private static final String ASSET_BASE_URL = "https://appassets.androidplatform.net/assets/web/";
    private static final String EXTRA_PAGE_QUERY = "xyz.kangnasi.interviewpractice.PAGE_QUERY";
    private static final long EXIT_CONFIRM_WINDOW_MS = 1800L;

    private WebView webView;
    private LinearLayout errorView;
    private TextView errorTitle;
    private TextView errorMessage;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private long lastBackPressedAt;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private Future<?> updateDownloadTask;

    protected abstract String getPageName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        applySystemBarInsets(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        errorView = createErrorView();
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        configureWebView();
        webView.loadUrl(getInitialUrl());
    }

    private void applySystemBarInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            Insets insets = windowInsets.getInsets(types);
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(types, Insets.NONE)
                    .build();
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
    }

    private String getInitialUrl() {
        String query = getIntent().getStringExtra(EXTRA_PAGE_QUERY);
        if (query == null || query.isBlank()) {
            return ASSET_BASE_URL + getPageName();
        }
        return ASSET_BASE_URL + getPageName() + "?" + query;
    }

    private LinearLayout createErrorView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int horizontalPadding = dp(28);
        layout.setPadding(horizontalPadding, dp(28), horizontalPadding, dp(28));
        layout.setBackgroundColor(Color.rgb(248, 250, 252));
        layout.setVisibility(View.GONE);

        errorTitle = new TextView(this);
        errorTitle.setText("页面加载失败");
        errorTitle.setTextColor(Color.rgb(24, 32, 44));
        errorTitle.setTextSize(24);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setTypeface(errorTitle.getTypeface(), android.graphics.Typeface.BOLD);
        layout.addView(errorTitle);

        errorMessage = new TextView(this);
        errorMessage.setText("请检查网络连接或后端服务后重试。");
        errorMessage.setTextColor(Color.rgb(95, 111, 134));
        errorMessage.setTextSize(15);
        errorMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.setMargins(0, dp(12), 0, dp(22));
        layout.addView(errorMessage, messageParams);

        Button retryButton = new Button(this);
        retryButton.setText("重试");
        retryButton.setAllCaps(false);
        retryButton.setOnClickListener(view -> {
            hideError();
            webView.reload();
        });
        layout.addView(retryButton, new LinearLayout.LayoutParams(dp(144), dp(48)));

        return layout;
    }

    private void showError(String title, String message) {
        errorTitle.setText(title);
        errorMessage.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (errorView.getVisibility() == View.VISIBLE) {
            hideError();
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        if (!"index.html".equals(getPageName())) {
            finish();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt <= EXIT_CONFIRM_WINDOW_MS) {
            super.onBackPressed();
            return;
        }

        lastBackPressedAt = now;
        Toast.makeText(this, "再按一次退出 APP", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST_CODE || filePathCallback == null) {
            return;
        }

        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            result = collectAllowedUploadUris(data);
            if (result.length == 0) {
                Toast.makeText(this, "请选择 Markdown 或 ZIP 文件", Toast.LENGTH_SHORT).show();
                result = null;
            }
        }

        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.destroy();
        }
        if (updateDownloadTask != null) {
            updateDownloadTask.cancel(true);
        }
        downloadExecutor.shutdownNow();
        super.onDestroy();
    }

    private Uri[] collectAllowedUploadUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int index = 0; index < data.getClipData().getItemCount(); index += 1) {
                Uri uri = data.getClipData().getItemAt(index).getUri();
                if (isAllowedUploadUri(uri)) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null && isAllowedUploadUri(data.getData())) {
            uris.add(data.getData());
        }
        return uris.toArray(new Uri[0]);
    }

    private boolean isAllowedUploadUri(Uri uri) {
        String filename = queryDisplayName(uri);
        if (filename == null || filename.isBlank()) {
            return true;
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown")
                || lowerName.endsWith(".zip");
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameIndex >= 0) {
                    return cursor.getString(displayNameIndex);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openPage(String page, String paramsJson, boolean replace) {
        Class<? extends Activity> activityClass = activityClassForPage(page);
        Intent intent = new Intent(this, activityClass);
        String query = queryFromJson(paramsJson);
        if (!query.isBlank()) {
            intent.putExtra(EXTRA_PAGE_QUERY, query);
        }
        startActivity(intent);
        if (replace) {
            finish();
        }
    }

    private void openAssetPage(Uri uri) {
        String page = uri.getLastPathSegment();
        if (page == null || page.isBlank()) {
            page = "index.html";
        }
        Intent intent = new Intent(this, activityClassForPage(page));
        String query = uri.getEncodedQuery();
        if (query != null && !query.isBlank()) {
            intent.putExtra(EXTRA_PAGE_QUERY, query);
        }
        startActivity(intent);
    }

    private boolean isAssetPage(Uri uri) {
        return "appassets.androidplatform.net".equals(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/assets/web/")
                && uri.getPath().endsWith(".html");
    }

    private String queryFromJson(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return "";
        }

        try {
            JSONObject params = new JSONObject(paramsJson);
            Uri.Builder builder = new Uri.Builder();
            Iterator<String> keys = params.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = params.opt(key);
                if (value != null && value != JSONObject.NULL) {
                    builder.appendQueryParameter(key, String.valueOf(value));
                }
            }
            String query = builder.build().getEncodedQuery();
            return query == null ? "" : query;
        } catch (JSONException exception) {
            return "";
        }
    }

    private Class<? extends Activity> activityClassForPage(String page) {
        switch (page) {
            case "login.html":
                return LoginActivity.class;
            case "user.html":
                return UserActivity.class;
            case "wrong-book.html":
                return WrongBookActivity.class;
            case "favorites.html":
                return FavoritesActivity.class;
            case "answered-questions.html":
                return AnsweredQuestionsActivity.class;
            case "excluded-questions.html":
                return ExcludedQuestionsActivity.class;
            case "statistics.html":
                return StatisticsActivity.class;
            case "ai-settings.html":
                return AiSettingsActivity.class;
            case "admin.html":
                return AdminActivity.class;
            case "admin-documents-upload.html":
                return AdminDocumentUploadActivity.class;
            case "admin-document-upload-result.html":
                return AdminDocumentUploadResultActivity.class;
            case "admin-documents.html":
                return AdminDocumentListActivity.class;
            case "admin-document-detail.html":
                return AdminDocumentDetailActivity.class;
            case "admin-import-jobs.html":
                return AdminImportJobListActivity.class;
            case "admin-import-job-detail.html":
                return AdminImportJobDetailActivity.class;
            case "admin-questions.html":
                return AdminQuestionListActivity.class;
            case "admin-question-detail.html":
                return AdminQuestionDetailActivity.class;
            case "profile.html":
                return ProfileActivity.class;
            case "index.html":
            default:
                return MainActivity.class;
        }
    }

    private void installApkFromUrl(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            Toast.makeText(this, "下载地址为空", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始下载更新", Toast.LENGTH_SHORT).show();
        updateDownloadTask = downloadExecutor.submit(() -> {
            try {
                File apk = downloadUpdateApk(downloadUrl);
                runOnUiThread(() -> openApkInstaller(apk));
            } catch (IOException exception) {
                runOnUiThread(() -> Toast.makeText(
                        WebPageActivity.this,
                        "更新下载失败，请稍后重试",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private File downloadUpdateApk(String downloadUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.connect();
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IOException("Unexpected update response: " + connection.getResponseCode());
        }

        File updatesDir = new File(getCacheDir(), "updates");
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw new IOException("Cannot create updates directory");
        }

        File apk = new File(updatesDir, "interview-practice-update.apk");
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(apk)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        return apk;
    }

    private void openApkInstaller(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "请先允许安装未知来源应用", Toast.LENGTH_SHORT).show();
            new AndroidBridge().openInstallPermissionSettings();
            return;
        }

        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "未找到 APK 安装器", Toast.LENGTH_SHORT).show();
        }
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("mailto".equals(scheme) || "tel".equals(scheme)) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(WebPageActivity.this, "未找到可打开该链接的应用", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (isAssetPage(uri)) {
                openAssetPage(uri);
                return true;
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url.startsWith(ASSET_BASE_URL)) {
                hideError();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showError("页面加载失败", "请检查网络连接或重新构建前端资源后重试。");
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (request.isForMainFrame()) {
                showError("页面加载失败", "页面资源返回异常，请稍后重试。");
            }
        }
    }

    private class AppWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (WebPageActivity.this.filePathCallback != null) {
                WebPageActivity.this.filePathCallback.onReceiveValue(null);
            }
            WebPageActivity.this.filePathCallback = filePathCallback;

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "text/markdown",
                    "text/plain",
                    "application/zip",
                    "application/x-zip-compressed"
            });
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivityForResult(Intent.createChooser(intent, "选择上传文件"), FILE_CHOOSER_REQUEST_CODE);
            } catch (ActivityNotFoundException exception) {
                WebPageActivity.this.filePathCallback = null;
                filePathCallback.onReceiveValue(null);
                Toast.makeText(WebPageActivity.this, "未找到文件选择器", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String getAppVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public int getVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public String getVersionName() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public String getPackageName() {
            return WebPageActivity.this.getPackageName();
        }

        @JavascriptInterface
        public void openPage(String page, String paramsJson, boolean replace) {
            runOnUiThread(() -> WebPageActivity.this.openPage(page, paramsJson, replace));
        }

        @JavascriptInterface
        public void installApkFromUrl(String downloadUrl) {
            runOnUiThread(() -> WebPageActivity.this.installApkFromUrl(downloadUrl));
        }

        @JavascriptInterface
        public void openInstallPermissionSettings() {
            runOnUiThread(() -> {
                Intent intent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse("package:" + getPackageName()));
                } else {
                    intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                }
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException exception) {
                    Toast.makeText(WebPageActivity.this, "无法打开安装权限设置", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
