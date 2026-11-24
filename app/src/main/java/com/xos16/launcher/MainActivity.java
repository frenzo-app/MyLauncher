package com.xos16.launcher;

import android.app.Activity;
import android.content.Context;
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
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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

    private static final int FILE_CHOOSER_REQUEST_CODE = 1004;
    private static final int UNINSTALL_REQUEST_CODE = 2005;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableEdgeToEdge();

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(webView);

        setupWebView();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return;
                }
                if (webView != null) {
                    webView.evaluateJavascript("if(typeof onAndroidBack === 'function'){ onAndroidBack(); }", null);
                }
            }
        });
    }

    private void enableEdgeToEdge() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView mWebView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams
            ) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
                filePathCallback = callback;

                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    String[] mimeTypes = {"image/png", "image/jpeg", "image/webp"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                    Intent chooser = Intent.createChooser(intent, "Choose Icon Image");
                    startActivityForResult(chooser, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else if (requestCode == UNINSTALL_REQUEST_CODE) {
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                        "if(typeof refreshInstalledApps === 'function'){ refreshInstalledApps(); }",
                        null
                ));
            }
        }
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
                    Uri packageUri = Uri.parse("package:" + packageName);
                    Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
                    uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    startActivityForResult(uninstallIntent, UNINSTALL_REQUEST_CODE);
                } catch (Exception e) {
                    try {
                        Intent fallbackIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(fallbackIntent);
                    } catch (Exception ignored) {
                    }
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
