package com.xos16.launcher;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri dataUri = result.getData().getData();
                    if (dataUri != null) {
                        results = new Uri[]{dataUri};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new LauncherBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("if(typeof refreshInstalledApps === 'function') refreshInstalledApps();", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                fileChooserLauncher.launch(Intent.createChooser(intent, "Select Icon Image"));
                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.evaluateJavascript("if(typeof refreshInstalledApps === 'function') refreshInstalledApps();", null);
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("if(typeof onAndroidBack === 'function') onAndroidBack();", null);
    }

    public class LauncherBridge {

        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray jsonArray = new JSONArray();
            try {
                PackageManager pm = getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
                Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));

                for (ResolveInfo info : apps) {
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;

                    String label = info.loadLabel(pm).toString();
                    String category = detectCategory(pkg, info.activityInfo.applicationInfo);
                    String iconBase64 = drawableToBase64(info.loadIcon(pm));

                    JSONObject obj = new JSONObject();
                    obj.put("packageName", pkg);
                    obj.put("name", label);
                    obj.put("category", category);
                    obj.put("systemIcon", iconBase64);
                    jsonArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return jsonArray.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName, int x, int y, int width, int height) {
            runOnUiThread(() -> {
                try {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        ActivityOptions options = ActivityOptions.makeScaleUpAnimation(
                                webView, x, y, width, height
                        );
                        startActivity(launchIntent, options.toBundle());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void openAppInfo(String packageName) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void uninstallApp(String packageName) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        private String detectCategory(String pkg, ApplicationInfo appInfo) {
            String p = pkg.toLowerCase();
            if (p.contains("whatsapp") || p.contains("messenger") || p.contains("telegram") ||
                p.contains("imo") || p.contains("dialer") || p.contains("contacts") ||
                p.contains("mms") || p.contains("phone") || p.contains("gmail")) {
                return "Communication";
            }
            if (p.contains("youtube") || p.contains("tiktok") || p.contains("spotify") ||
                p.contains("mxtech") || p.contains("video") || p.contains("music") ||
                p.contains("netflix") || p.contains("cine")) {
                return "Entertainment";
            }
            if (p.contains("facebook") || p.contains("instagram") || p.contains("twitter") ||
                p.contains("x.") || p.contains("snapchat") || p.contains("threads")) {
                return "Social";
            }
            if (p.contains("game") || p.contains("freefire") || p.contains("pubg") ||
                p.contains("clash") || p.contains("ludo")) {
                return "Games";
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return "Games";
                if (appInfo.category == ApplicationInfo.CATEGORY_SOCIAL) return "Social";
                if (appInfo.category == ApplicationInfo.CATEGORY_AUDIO ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO) return "Entertainment";
            }
            return "Tools";
        }

        private String drawableToBase64(Drawable drawable) {
            try {
                Bitmap bitmap;
                if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                } else {
                    int w = Math.max(drawable.getIntrinsicWidth(), 1);
                    int h = Math.max(drawable.getIntrinsicHeight(), 1);
                    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                }
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 112, 112, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.PNG, 85, baos);
                return "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
