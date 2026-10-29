package com.taqwimi.mahaam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * كل ما يخص عرض الإشعار في مكان واحد، ليستخدمه المنبّه والإشعار الفوري معاً.
 */
public final class Notifier {

    /**
     * معرّف قناة جديد (v2). إعدادات القناة (الاهتزاز والأهمية) لا تتغير بعد إنشائها على الجهاز،
     * ولذلك لا يكفي تعديل القناة القديمة "taqwimi_reminders"؛ نحتاج قناة بمعرّف جديد.
     */
    private static final String TAG = "TaqwimiAlarm";

    public static final String CHANNEL_ID = "taqwimi_reminders_v2";

    /** هل نُشغّل الشاشة عند وصول التنبيه؟ ضعها false إذا لم ترد إضاءة الشاشة. */
    private static final boolean WAKE_SCREEN = true;
    private static final long WAKE_MS = 10000L;

    /** نفس الأيقونة المستخدمة حالياً في التطبيق (android.R.drawable.ic_popup_reminder). */
    private static final int SMALL_ICON = 0x0108009b;

    private Notifier() {}

    /** ينشئ قناة الإشعارات (أهمية عالية + صوت + اهتزاز). آمنة للاستدعاء المتكرر. */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "تذكيرات تقويمي", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيهات المناسبات والمهام من تطبيق تقويمي");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
            ch.enableLights(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
            // لا نُسقط التطبيق بسبب القناة
        }
    }

    /** يعرض إشعاراً. wakeScreen=true يُشعل الشاشة إذا كانت مطفأة. */
    @SuppressWarnings("deprecation")
    public static void show(Context ctx, int id, String title, String body, boolean wakeScreen) {
        try {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            ensureChannel(ctx);

            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, open, piFlags);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(ctx, CHANNEL_ID);
            } else {
                // قبل أندرويد 8 لا توجد قنوات: بدون هذين السطرين يصل الإشعار صامتاً
                b = new Notification.Builder(ctx);
                b.setPriority(Notification.PRIORITY_HIGH);
                b.setDefaults(Notification.DEFAULT_ALL);
            }

            String safeBody = body == null ? "" : body;
            b.setSmallIcon(SMALL_ICON)
                    .setContentTitle(title == null ? "تقويمي" : title)
                    .setContentText(safeBody)
                    .setStyle(new Notification.BigTextStyle().bigText(safeBody))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);

            if (wakeScreen && WAKE_SCREEN) wakeScreen(ctx);
            nm.notify(id, b.build());
        } catch (Throwable e) {
            // لا نُسقط المستقبِل، لكن نسجّل السبب (مثلاً غياب إذن POST_NOTIFICATIONS)
            Log.w(TAG, "show notification failed", e);
        }
    }

    /** يُشعل الشاشة لمدة قصيرة إن كانت مطفأة (يحتاج إذن WAKE_LOCK وهو موجود في المانيفست). */
    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isInteractive()) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "taqwimi:reminder");
            wl.acquire(WAKE_MS); // يُحرَّر تلقائياً بعد المهلة
        } catch (Throwable ignored) {
        }
    }
}
