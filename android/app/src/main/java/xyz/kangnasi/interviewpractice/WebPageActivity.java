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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class WebPageActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST_CODE = 1201;
    private static final String ASSET_BASE_URL = "https://appassets.androidplatform.net/assets/web/";
    private static final String EXTRA_PAGE_QUERY = "xyz.kangnasi.interviewpractice.PAGE_QUERY";
    private static final long EXIT_CONFIRM_WINDOW_MS = 1800L;
    private static final int AI_STREAM_HAS_TEXT = 1;
    private static final int AI_STREAM_DONE = 2;

    private WebView webView;
    private LinearLayout errorView;
    private TextView errorTitle;
    private TextView errorMessage;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private long lastBackPressedAt;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService aiExecutor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, AiStreamRequest> aiStreamRequests = new ConcurrentHashMap<>();
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
        cancelAllAiStreams();
        aiExecutor.shutdownNow();
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

    private void startAiChatStream(
            String requestId,
            String endpoint,
            String apiKey,
            String requestBodyJson
    ) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        String currentUrl = webView == null ? null : webView.getUrl();
        if (currentUrl == null || !currentUrl.startsWith(ASSET_BASE_URL)) {
            emitAiStreamEvent(requestId, "error", null, "当前页面无权调用原生大模型请求");
            return;
        }
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            emitAiStreamEvent(requestId, "error", null, "大模型请求地址或 SK 为空");
            return;
        }

        final URL endpointUrl;
        final JSONObject requestBody;
        try {
            endpointUrl = new URL(endpoint.trim());
            String protocol = endpointUrl.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new IOException("仅支持 HTTP 或 HTTPS 请求地址");
            }
            requestBody = new JSONObject(requestBodyJson == null ? "{}" : requestBodyJson);
            requestBody.put("stream", true);
        } catch (IOException | JSONException exception) {
            emitAiStreamEvent(requestId, "error", null, "大模型请求参数无效：" + safeErrorMessage(exception));
            return;
        }

        AiStreamRequest request = new AiStreamRequest();
        AiStreamRequest previous = aiStreamRequests.put(requestId, request);
        if (previous != null) {
            previous.cancel();
        }

        try {
            request.future = aiExecutor.submit(
                    () -> executeAiChatStream(requestId, endpointUrl, apiKey.trim(), requestBody, request)
            );
            if (request.canceled) {
                request.future.cancel(true);
            }
        } catch (RuntimeException exception) {
            aiStreamRequests.remove(requestId, request);
            emitAiStreamEvent(requestId, "error", null, "无法启动大模型请求");
        }
    }

    private void executeAiChatStream(
            String requestId,
            URL endpoint,
            String apiKey,
            JSONObject requestBody,
            AiStreamRequest request
    ) {
        boolean receivedText = false;
        HttpURLConnection connection = null;
        try {
            if (request.canceled) {
                return;
            }

            byte[] bodyBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) endpoint.openConnection();
            request.connection = connection;
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(300000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bodyBytes.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(bodyBytes);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String message = readAiHttpError(connection, statusCode);
                if (!request.canceled) {
                    emitAiStreamEvent(requestId, "error", null, message);
                }
                return;
            }

            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                readTextStream(connection.getInputStream());
                if (!request.canceled) {
                    emitAiStreamEvent(
                            requestId,
                            "error",
                            null,
                            "接口返回了 HTML 页面，请检查基础请求地址是否缺少 /v1"
                    );
                }
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                StringBuilder eventData = new StringBuilder();
                String line;
                while (!request.canceled && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (eventData.length() > 0) {
                            int result = processAiStreamData(requestId, eventData.toString(), request);
                            receivedText = receivedText || (result & AI_STREAM_HAS_TEXT) != 0;
                            eventData.setLength(0);
                            if ((result & AI_STREAM_DONE) != 0) {
                                finishAiStream(requestId, request, receivedText);
                                return;
                            }
                        }
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (eventData.length() > 0) {
                            eventData.append('\n');
                        }
                        eventData.append(line.substring(5).trim());
                        continue;
                    }

                    String trimmedLine = line.trim();
                    if (eventData.length() == 0 && trimmedLine.startsWith("{")) {
                        int result = processAiStreamData(requestId, trimmedLine, request);
                        receivedText = receivedText || (result & AI_STREAM_HAS_TEXT) != 0;
                        if ((result & AI_STREAM_DONE) != 0) {
                            finishAiStream(requestId, request, receivedText);
                            return;
                        }
                    }
                }

                if (!request.canceled && eventData.length() > 0) {
                    int result = processAiStreamData(requestId, eventData.toString(), request);
                    receivedText = receivedText || (result & AI_STREAM_HAS_TEXT) != 0;
                }
            }

            if (!request.canceled) {
                finishAiStream(requestId, request, receivedText);
            }
        } catch (IOException | JSONException exception) {
            if (!request.canceled) {
                emitAiStreamEvent(
                        requestId,
                        "error",
                        null,
                        "原生大模型请求失败：" + safeErrorMessage(exception)
                );
            }
        } finally {
            request.connection = null;
            if (connection != null) {
                connection.disconnect();
            }
            aiStreamRequests.remove(requestId, request);
        }
    }

    private int processAiStreamData(
            String requestId,
            String data,
            AiStreamRequest request
    ) throws JSONException, IOException {
        String trimmedData = data.trim();
        if (trimmedData.isEmpty()) {
            return 0;
        }
        if ("[DONE]".equals(trimmedData)) {
            return AI_STREAM_DONE;
        }

        JSONObject payload = new JSONObject(trimmedData);
        JSONObject error = payload.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message", "大模型流式响应返回错误");
            throw new IOException(message);
        }

        JSONArray choices = payload.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return 0;
        }
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            return 0;
        }

        String text = "";
        JSONObject delta = choice.optJSONObject("delta");
        if (delta != null) {
            text = extractAiText(delta.opt("content"));
            if (text.isEmpty()) {
                text = delta.optString("refusal", "");
            }
        }
        if (text.isEmpty()) {
            JSONObject message = choice.optJSONObject("message");
            if (message != null) {
                text = extractAiText(message.opt("content"));
                if (text.isEmpty()) {
                    text = message.optString("refusal", "");
                }
            }
        }
        if (text.isEmpty()) {
            text = choice.optString("text", "");
        }

        if (!text.isEmpty() && !request.canceled) {
            emitAiStreamEvent(requestId, "delta", text, null);
            return AI_STREAM_HAS_TEXT;
        }
        return 0;
    }

    private String extractAiText(Object content) {
        if (content instanceof String) {
            return (String) content;
        }
        if (!(content instanceof JSONArray)) {
            return "";
        }

        JSONArray parts = (JSONArray) content;
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < parts.length(); index += 1) {
            Object part = parts.opt(index);
            if (part instanceof String) {
                text.append((String) part);
                continue;
            }
            if (!(part instanceof JSONObject)) {
                continue;
            }
            Object partText = ((JSONObject) part).opt("text");
            if (partText instanceof String) {
                text.append((String) partText);
            } else if (partText instanceof JSONObject) {
                text.append(((JSONObject) partText).optString("value", ""));
            }
        }
        return text.toString();
    }

    private void finishAiStream(String requestId, AiStreamRequest request, boolean receivedText) {
        if (request.canceled) {
            return;
        }
        if (!receivedText) {
            emitAiStreamEvent(requestId, "error", null, "大模型流式响应中没有可展示的文本");
            return;
        }
        emitAiStreamEvent(requestId, "complete", null, null);
    }

    private String readAiHttpError(HttpURLConnection connection, int statusCode) {
        String responseBody = readTextStream(connection.getErrorStream());
        String serverMessage = "";
        if (!responseBody.isBlank()) {
            try {
                JSONObject payload = new JSONObject(responseBody);
                JSONObject error = payload.optJSONObject("error");
                if (error != null) {
                    serverMessage = error.optString("message", "");
                }
                if (serverMessage.isBlank()) {
                    serverMessage = payload.optString("message", "");
                }
            } catch (JSONException ignored) {
                serverMessage = responseBody.replaceAll("\\s+", " ").trim();
            }
        }
        if (serverMessage.length() > 400) {
            serverMessage = serverMessage.substring(0, 400) + "…";
        }
        String suffix = serverMessage.isBlank() ? "" : "：" + serverMessage;
        return "大模型请求失败（HTTP " + statusCode + "）" + suffix;
    }

    private String readTextStream(InputStream stream) {
        if (stream == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        ))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1 && text.length() < 32768) {
                int remaining = 32768 - text.length();
                text.append(buffer, 0, Math.min(read, remaining));
            }
        } catch (IOException ignored) {
            return text.toString();
        }
        return text.toString();
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private void emitAiStreamEvent(String requestId, String type, String text, String message) {
        JSONObject event = new JSONObject();
        try {
            event.put("requestId", requestId);
            event.put("type", type);
            if (text != null) {
                event.put("text", text);
            }
            if (message != null) {
                event.put("message", message);
            }
        } catch (JSONException ignored) {
            return;
        }

        String script = "window.__onAndroidAiStream && window.__onAndroidAiStream(" + event + ");";
        runOnUiThread(() -> {
            if (webView != null && !isDestroyed()) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private void cancelAiStream(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        AiStreamRequest request = aiStreamRequests.remove(requestId);
        if (request != null) {
            request.cancel();
        }
    }

    private void cancelAllAiStreams() {
        for (AiStreamRequest request : aiStreamRequests.values()) {
            request.cancel();
        }
        aiStreamRequests.clear();
    }

    private static class AiStreamRequest {
        volatile boolean canceled;
        volatile HttpURLConnection connection;
        volatile Future<?> future;

        void cancel() {
            canceled = true;
            HttpURLConnection activeConnection = connection;
            if (activeConnection != null) {
                activeConnection.disconnect();
            }
            Future<?> activeFuture = future;
            if (activeFuture != null) {
                activeFuture.cancel(true);
            }
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
        public void startAiChatStream(
                String requestId,
                String endpoint,
                String apiKey,
                String requestBodyJson
        ) {
            runOnUiThread(() -> WebPageActivity.this.startAiChatStream(
                    requestId,
                    endpoint,
                    apiKey,
                    requestBodyJson
            ));
        }

        @JavascriptInterface
        public void cancelAiChatStream(String requestId) {
            WebPageActivity.this.cancelAiStream(requestId);
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
