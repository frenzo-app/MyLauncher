package com.example.mylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enableEdgeToEdge();

        webView = findViewById(R.id.launcherWebView);
        setupWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null) {
                    webView.evaluateJavascript("if(typeof onAndroidBack === 'function'){ onAndroidBack(); }", null);
                }
            }
        });
    }

    private void enableEdgeToEdge() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setBackgroundColor(0x00000000);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                if (url.startsWith("tel:")) {
                    openDialer();
                    return true;
                }
                if (url.startsWith("sms:") || url.startsWith("smsto:")) {
                    openMessages();
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        });

        webView.addJavascriptInterface(new LauncherBridge(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public void openDialer() {
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void openMessages() {
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_APP_MESSAGING);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent smsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"));
                    smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(smsIntent);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public class LauncherBridge {
        private final Context context;

        LauncherBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray jsonArray = new JSONArray();
            try {
                PackageManager pm = context.getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> resolveInfoList;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resolveInfoList = pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0));
                } else {
                    resolveInfoList = pm.queryIntentActivities(mainIntent, 0);
                }

                Collections.sort(resolveInfoList, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        String nameA = a.loadLabel(pm).toString();
                        String nameB = b.loadLabel(pm).toString();
                        return nameA.compareToIgnoreCase(nameB);
                    }
                });

                List<String> seenPackages = new ArrayList<>();

                for (ResolveInfo info : resolveInfoList) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(context.getPackageName()) || seenPackages.contains(pkgName)) {
                        continue;
                    }
                    seenPackages.add(pkgName);

                    String appName = info.loadLabel(pm).toString();
                    Drawable iconDrawable = info.loadIcon(pm);
                    String base64Icon = drawableToBase64(iconDrawable);
                    String category = detectCategory(pkgName, info.activityInfo.applicationInfo);

                    JSONObject obj = new JSONObject();
                    obj.put("packageName", pkgName);
                    obj.put("name", appName);
                    obj.put("systemIcon", base64Icon);
                    obj.put("category", category);
                    jsonArray.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return jsonArray.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName, int left, int top, int width, int height) {
            if (packageName == null || packageName.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    if (packageName.toLowerCase().contains("dialer")) {
                        openDialer();
                        return;
                    }
                    if (packageName.toLowerCase().contains("mms") || packageName.toLowerCase().contains("messaging")) {
                        openMessages();
                        return;
                    }
                    PackageManager pm = context.getPackageManager();
                    Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void openDialer() {
            MainActivity.this.openDialer();
        }

        @JavascriptInterface
        public void openMessages() {
            MainActivity.this.openMessages();
        }

        @JavascriptInterface
        public void openAppInfo(String packageName) {
            if (packageName == null || packageName.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
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
            if (packageName == null || packageName.isEmpty()) return;
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
            String lower = pkg.toLowerCase();
            if (lower.contains("whatsapp") || lower.contains("messenger") || lower.contains("telegram")
                    || lower.contains("dialer") || lower.contains("mms") || lower.contains("messages")
                    || lower.contains("imo") || lower.contains("viber")) {
                return "Communication";
            }
            if (lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify")
                    || lower.contains("mxtech") || lower.contains("tiktok") || lower.contains("music")) {
                return "Entertainment";
            }
            if (lower.contains("facebook") || lower.contains("instagram") || lower.contains("twitter")
                    || lower.contains("snapchat") || lower.contains("threads")) {
                return "Social";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return "Games";
            }
            if (lower.contains("game") || lower.contains("freefire") || lower.contains("pubg")) {
                return "Games";
            }
            return "Tools";
        }

        private String drawableToBase64(Drawable drawable) {
            if (drawable == null) return "";
            try {
                Bitmap bitmap;
                if (drawable instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                } else {
                    int w = Math.max(drawable.getIntrinsicWidth(), 96);
                    int h = Math.max(drawable.getIntrinsicHeight(), 96);
                    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                }
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.PNG, 85, outputStream);
                byte[] byteArray = outputStream.toByteArray();
                return "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
