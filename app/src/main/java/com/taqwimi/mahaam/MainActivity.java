package com.taqwimi.mahaam;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.List;

/**
 * الواجهة الرئيسية: WebView يعرض assets/index.html ويتصل بالنظام عبر الجسر AndroidBridge.
 *
 * ملاحظة: هذا الملف أُعيد بناؤه من البايتكود المترجم لنسخة taqwimi-1.apk لأن الشيفرة المصدرية
 * الأصلية لم تكن متاحة. السلوك مطابق للأصل، مع هذه الفروقات المقصودة فقط:
 *   1) Bridge.scheduleAlarms صار يقبل 4 وسائط (يطابق ما ترسله الواجهة)، مع بقاء النسخة ذات 3 وسائط.
 *   2) إضافة Bridge.scheduleTestAlarm لاختبار التنبيه والتطبيق مغلق.
 *   3) الإشعارات تمر عبر Notifier (قناة جديدة بصوت واهتزاز).
 *   4) hasNotificationPermission لا يستدعي دالة غير موجودة في أندرويد 5-6 (كان يسبب انهياراً).
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_SAVE_FILE = 1002;
    private static final int REQ_NOTIF_PERMISSION = 1003;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingBytes;
    private String pendingName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.ensureChannel(this);

        webView = new WebView(this);
        webView.setBackgroundColor(0xff303030);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (String res : request.getResources()) {
                            if ("android.webkit.resource.NOTIFICATION".equals(res)) {
                                if (Build.VERSION.SDK_INT >= 33
                                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                                        != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissions(
                                            new String[]{"android.permission.POST_NOTIFICATIONS"},
                                            REQ_NOTIF_PERMISSION);
                                }
                                request.grant(new String[]{res});
                                return;
                            }
                        }
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQ_NOTIF_PERMISSION);
        }

        // عوّض أي تنبيه فات والتطبيق مغلق، وأعد ضبط المنبّه القادم
        AlarmScheduler.rescheduleAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript(
                "(function(){try{return window.__onBack ? window.__onBack() : false;}catch(e){return false;}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) MainActivity.super.onBackPressed();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF_PERMISSION) {
            final boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(
                            "window.__notifPermission && window.__notifPermission(" + granted + ")", null);
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == REQ_SAVE_FILE) {
            boolean ok = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingBytes != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) {
                        try {
                            os.write(pendingBytes);
                            os.flush();
                            ok = true;
                        } finally {
                            os.close();
                        }
                    }
                } catch (Exception e) {
                    ok = false;
                }
            }
            String name = pendingName;
            pendingBytes = null;
            pendingName = null;
            notifyJs(ok, name);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ------------------------------------------------------------------
    // إشعار فوري (زر "اختبار التنبيه" وأول تشغيل). التنبيهات المجدولة تمر عبر AlarmScheduler.
    // ------------------------------------------------------------------
    private void showNotification(String title, String body) {
        int id = (int) (System.currentTimeMillis() % 2147483647L);
        Notifier.show(this, id, title == null ? "تقويمي" : title, body == null ? "" : body, false);
    }

    private void notifyJs(final boolean ok, final String name) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("window.__saved && window.__saved(" + ok + ","
                        + JSONObject.quote(name == null ? "" : name) + ")", null);
            }
        });
    }

    // ------------------------------------------------------------------
    // التعامل مع الروابط
    // ------------------------------------------------------------------
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if ("file".equals(scheme) || "about".equals(scheme) || "data".equals(scheme)
                || "blob".equals(scheme)) {
            return false;
        }

        if ("mailto".equals(scheme)) {
            try {
                Intent i = new Intent(Intent.ACTION_SENDTO, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "لا يوجد تطبيق بريد مثبّت", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        if ("tel".equals(scheme)) {
            try {
                Intent i = new Intent(Intent.ACTION_DIAL, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "لا يمكن فتح تطبيق الاتصال", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if ("sms".equals(scheme) || "smsto".equals(scheme)) {
            try {
                Intent i = new Intent(Intent.ACTION_SENDTO, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }
        if ("geo".equals(scheme)) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }

        String host = uri.getHost();
        if (host != null) host = host.toLowerCase();
        String path = uri.getPath();
        if (path == null) path = "";
        String full = uri.toString();

        if (isGoogleMapsUrl(host, path, full)) {
            if (!openInGoogleMaps(uri)) openInBrowser(uri);
            return true;
        }
        if (host != null && (host.contains("wa.me") || host.contains("whatsapp.com"))) {
            if (!openWithPackage(uri, "com.whatsapp")) openInBrowser(uri);
            return true;
        }
        openInBrowser(uri);
        return true;
    }

    private boolean isGoogleMapsUrl(String host, String path, String full) {
        if (host == null) return false;
        if (host.contains("maps.google.com")) return true;
        if (host.contains("maps.app.goo.gl")) return true;
        if (host.contains("goo.gl") && path.contains("maps")) return true;
        if (host.contains("google.com") && path.startsWith("/maps")) return true;
        if (host.contains("google.com") && full.contains("/maps/")) return true;
        return false;
    }

    private boolean openInGoogleMaps(Uri uri) {
        try {
            Uri geo;
            String q = uri.getQueryParameter("q");
            if (q != null && !q.isEmpty()) {
                geo = Uri.parse("geo:0,0?q=" + Uri.encode(q));
            } else {
                String full = uri.toString();
                String place = "";
                int start = -1;
                if (full.contains("/maps/place/")) {
                    start = full.indexOf("/maps/place/") + 12;
                } else if (full.contains("/maps/search/")) {
                    start = full.indexOf("/maps/search/") + 13;
                }
                if (start >= 0) {
                    int end = full.indexOf("/", start);
                    if (end < 0) end = full.indexOf("?", start);
                    if (end < 0) end = full.length();
                    place = Uri.decode(full.substring(start, end)).replace("+", " ");
                }
                geo = Uri.parse("geo:0,0?q=" + Uri.encode(place.isEmpty() ? full : place));
            }
            Intent i = new Intent(Intent.ACTION_VIEW, geo);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(i, 0);
            if (handlers != null && !handlers.isEmpty()) {
                startActivity(i);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean openWithPackage(Uri uri, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openInBrowser(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "لا يمكن فتح الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // الجسر بين JavaScript وأندرويد: window.AndroidBridge
    // ------------------------------------------------------------------
    public class Bridge {

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == PackageManager.PERMISSION_GRANTED;
            }
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                return nm != null && nm.areNotificationsEnabled();
            }
            return true;
        }

        @JavascriptInterface
        public void notify(final String title, final String body) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showNotification(title, body);
                }
            });
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                                REQ_NOTIF_PERMISSION);
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                            if (am != null && !am.canScheduleExactAlarms()) {
                                Intent i = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM");
                                i.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        /**
         * الصيغة التي ترسلها الواجهة: (قائمة المناسبات JSON، مفعّل، عدد الأيام، ساعات التكرار JSON).
         * كانت الدالة الأصلية تقبل 3 وسائط فقط فيفشل الاستدعاء ولا يُجدول شيء.
         */
        @JavascriptInterface
        public void scheduleAlarms(final String json, final boolean enabled, final int days,
                                   final String repeatsJson) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlarmScheduler.scheduleFromJson(MainActivity.this, json, enabled, days, repeatsJson);
                }
            });
        }

        /** توافق مع نسخ الواجهة القديمة (3 وسائط). */
        @JavascriptInterface
        public void scheduleAlarms(String json, boolean enabled, int days) {
            scheduleAlarms(json, enabled, days, null);
        }

        /** منبّه تجريبي بعد عدد من الثواني لاختبار الإيقاظ والتطبيق مغلق. */
        @JavascriptInterface
        public void scheduleTestAlarm(final int seconds) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlarmScheduler.scheduleTest(MainActivity.this, seconds);
                }
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (url == null || url.isEmpty()) return;
                    try {
                        handleUrl(Uri.parse(url));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void openMaps(final String location) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (location == null || location.isEmpty()) return;
                    Uri target;
                    if (location.startsWith("http://") || location.startsWith("https://")) {
                        target = Uri.parse(location);
                    } else {
                        target = Uri.parse("geo:0,0?q=" + Uri.encode(location));
                    }
                    if (!openInGoogleMaps(target)) {
                        Uri web;
                        if (location.startsWith("http")) {
                            web = Uri.parse(location);
                        } else {
                            web = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                                    + Uri.encode(location));
                        }
                        openInBrowser(web);
                    }
                }
            });
        }

        @JavascriptInterface
        public void dial(final String phone) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_DIAL);
                        i.setData(Uri.parse("tel:" + (phone == null ? "" : phone)));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "لا يمكن فتح تطبيق الاتصال",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void sendEmail(final String to, final String subject, final String body) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_SENDTO);
                        i.setData(Uri.parse("mailto:" + (to == null ? "" : to)));
                        if (subject != null && !subject.isEmpty()) {
                            i.putExtra(Intent.EXTRA_SUBJECT, subject);
                        }
                        if (body != null && !body.isEmpty()) {
                            i.putExtra(Intent.EXTRA_TEXT, body);
                        }
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "لا يوجد تطبيق بريد مثبّت",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        /** حفظ ملف: (الاسم، النوع، المحتوى base64) كما ترسله الواجهة. */
        @JavascriptInterface
        public void saveFile(final String name, final String mime, String base64) {
            try {
                pendingBytes = Base64.decode(base64, Base64.DEFAULT);
            } catch (Exception e) {
                notifyJs(false, "");
                return;
            }
            pendingName = name;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType(mime != null && mime.length() != 0 ? mime : "application/octet-stream");
                        i.putExtra(Intent.EXTRA_TITLE, name);
                        startActivityForResult(i, REQ_SAVE_FILE);
                    } catch (ActivityNotFoundException e) {
                        notifyJs(false, "");
                    }
                }
            });
        }
    }
}
