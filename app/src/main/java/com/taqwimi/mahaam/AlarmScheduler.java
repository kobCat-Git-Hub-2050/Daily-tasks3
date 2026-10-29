package com.taqwimi.mahaam;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * جدولة التنبيهات بتصميم "منبّه واحد متجدد":
 *
 *  - نحتفظ بقائمة المناسبات والإعدادات في SharedPreferences.
 *  - نحسب كل مواعيد التنبيه (قبل X يوم + قبل X ساعة) ونضبط منبّهاً واحداً فقط للأقرب منها.
 *  - عند انطلاقه يعرض المستحق، ثم يضبط المنبّه التالي، وهكذا.
 *
 * فوائد هذا التصميم:
 *  - لا نتجاوز حد 500 منبّه لكل تطبيق في أندرويد 12+ مهما كثرت المناسبات.
 *  - حذف مناسبة أو إنجازها يلغي تنبيهاتها تلقائياً (لا توجد منبّهات يتيمة).
 *  - بعد إعادة التشغيل أو تغيير الوقت/المنطقة الزمنية نعيد الحساب من القائمة المحفوظة.
 */
public final class AlarmScheduler {

    /** لتتبع العمل: adb logcat -s TaqwimiAlarm */
    private static final String TAG = "TaqwimiAlarm";

    static final String PREFS = "taqwimi_alarms";
    static final String KEY_LIST = "list";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_DAYS = "days";
    static final String KEY_REPEATS = "repeats";
    static final String KEY_LAST = "last_processed";

    static final String ACTION_ALARM = "com.taqwimi.mahaam.ACTION_REMINDER_ALARM";
    static final String ACTION_TEST = "com.taqwimi.mahaam.ACTION_TEST_ALARM";

    private static final int REQ_MAIN = 7001;
    private static final int REQ_TEST = 7002;
    private static final int NOTIF_TEST = 9001;
    private static final int NOTIF_SUMMARY = 9002;

    /** سماحية لأن المنبّه قد ينطلق قبل/بعد الموعد بأجزاء من الثانية. */
    private static final long SLACK_MS = 2000L;

    /** إذا استحق أكثر من هذا العدد دفعة واحدة (جهاز كان مغلقاً) نجمعها في إشعار واحد. */
    private static final int MAX_SEPARATE = 5;

    /** مصدر الوقت؛ يُستبدل في الاختبارات فقط. */
    interface Clock {
        long now();
    }

    static Clock clock = new Clock() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }
    };

    private AlarmScheduler() {}

    // ------------------------------------------------------------------
    // الواجهة العامة (تُستدعى من MainActivity و BootReceiver و AlarmReceiver)
    // ------------------------------------------------------------------

    /** توافق مع الواجهة القديمة ذات 3 وسائط: نُبقي التذكيرات المتكررة المحفوظة. */
    public static void scheduleFromJson(Context ctx, String json, boolean enabled, int days) {
        scheduleFromJson(ctx, json, enabled, days, null);
    }

    /**
     * تُستدعى كلما غيّر المستخدم شيئاً في التطبيق.
     *
     * @param json        مصفوفة المناسبات [{id,type,name,date,daysBefore}, ...]
     * @param enabled     هل التنبيهات مفعّلة
     * @param days        عدد الأيام قبل المناسبة
     * @param repeatsJson مصفوفة ساعات التذكير المتكرر مثل [12,6,4,1]؛ null = أبقِ المحفوظ
     */
    public static void scheduleFromJson(Context ctx, String json, boolean enabled, int days,
                                        String repeatsJson) {
        try {
            SharedPreferences sp = prefs(ctx);
            long now = clock.now();
            SharedPreferences.Editor ed = sp.edit();
            ed.putString(KEY_LIST, json == null ? "[]" : json);
            ed.putBoolean(KEY_ENABLED, enabled);
            ed.putInt(KEY_DAYS, days);
            if (repeatsJson != null) ed.putString(KEY_REPEATS, repeatsJson);
            // أي تنبيه فات وقته قبل هذه اللحظة لا يُطلق بسبب تعديل المستخدم لبياناته
            ed.putLong(KEY_LAST, Math.max(sp.getLong(KEY_LAST, 0L), now));
            ed.commit();

            List<ReminderPlanner.Trigger> all = enabled ? plan(sp) : new ArrayList<ReminderPlanner.Trigger>();
            arm(ctx, all, Math.max(now, sp.getLong(KEY_LAST, now)), enabled);
        } catch (Throwable e) {
            Log.w(TAG, "scheduleFromJson failed", e);
        }
    }

    /** عند فتح التطبيق / إعادة التشغيل / تحديث التطبيق / تغيير الوقت: عوّض الفائت وأعد الضبط. */
    public static void rescheduleAll(Context ctx) {
        processDue(ctx);
    }

    /** عند انطلاق المنبّه. */
    static void onAlarmFired(Context ctx) {
        processDue(ctx);
    }

    /** يلغي المنبّه المجدول. */
    public static void cancelAll(Context ctx) {
        try {
            AlarmManager am = alarmManager(ctx);
            if (am != null) am.cancel(mainPendingIntent(ctx));
        } catch (Throwable ignored) {
        }
    }

    /** منبّه تجريبي بعد عدد من الثواني، لاختبار الإيقاظ والتطبيق مغلق. */
    public static void scheduleTest(Context ctx, int seconds) {
        try {
            AlarmManager am = alarmManager(ctx);
            if (am == null) return;
            Intent i = new Intent(ctx, AlarmReceiver.class).setAction(ACTION_TEST);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_TEST, i, piFlags());
            setWakeAlarm(am, clock.now() + Math.max(5, seconds) * 1000L, pi);
        } catch (Throwable ignored) {
        }
    }

    static void showTestNotification(Context ctx) {
        Notifier.show(ctx, NOTIF_TEST, "تقويمي – مهام",
                "✅ وصل هذا التنبيه من المنبّه والتطبيق مغلق", true);
    }

    // ------------------------------------------------------------------
    // المعالجة
    // ------------------------------------------------------------------

    private static void processDue(Context ctx) {
        try {
            SharedPreferences sp = prefs(ctx);
            long now = clock.now();
            long upTo = now + SLACK_MS;
            // إن رجع المستخدم بالساعة للخلف يكون last أكبر من now؛ نُصحّحه
            long last = Math.min(sp.getLong(KEY_LAST, now), now);
            boolean enabled = sp.getBoolean(KEY_ENABLED, false);

            List<ReminderPlanner.Trigger> all = enabled ? plan(sp) : new ArrayList<ReminderPlanner.Trigger>();
            List<ReminderPlanner.Trigger> due = ReminderPlanner.due(all, last, upTo, now);

            // نسجّل أولاً، ثم نضبط المنبّه التالي، ثم نعرض: حتى لا تنقطع السلسلة لو فشل العرض
            sp.edit().putLong(KEY_LAST, upTo).commit();
            arm(ctx, all, upTo, enabled);
            notifyDue(ctx, due, now);
        } catch (Throwable e) {
            Log.w(TAG, "processDue failed", e);
        }
    }

    private static void notifyDue(Context ctx, List<ReminderPlanner.Trigger> due, long now) {
        if (due.isEmpty()) return;

        if (due.size() > MAX_SEPARATE) {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (ReminderPlanner.Trigger t : due) {
                if (n++ >= 10) {
                    sb.append("\n… و").append(due.size() - 10).append(" أخرى");
                    break;
                }
                if (sb.length() > 0) sb.append('\n');
                sb.append("• ").append(t.item.type);
                if (t.item.name.length() > 0) sb.append(" – ").append(t.item.name);
                sb.append(" (").append(t.item.date).append(')');
            }
            Notifier.show(ctx, NOTIF_SUMMARY, "تذكيرات المناسبات (" + due.size() + ")",
                    sb.toString(), true);
            return;
        }

        boolean wake = true; // نُشعل الشاشة مرة واحدة فقط
        for (ReminderPlanner.Trigger t : due) {
            // نفس معرّف الإشعار للمناسبة: التذكير الجديد يحل محل السابق بدل التكدس
            Notifier.show(ctx, t.item.id.hashCode(), ReminderPlanner.title(),
                    ReminderPlanner.body(t, now), wake);
            wake = false;
        }
    }

    /** يضبط (أو يلغي) المنبّه الوحيد على أقرب تنبيه بعد after. */
    private static void arm(Context ctx, List<ReminderPlanner.Trigger> all, long after,
                            boolean enabled) {
        AlarmManager am = alarmManager(ctx);
        if (am == null) return;
        PendingIntent pi = mainPendingIntent(ctx);
        ReminderPlanner.Trigger t = enabled ? ReminderPlanner.next(all, after) : null;
        if (t == null) {
            am.cancel(pi);
            Log.i(TAG, "no upcoming reminders; alarm cancelled");
            return;
        }
        Log.i(TAG, "next alarm at " + new java.util.Date(t.at) + " for event " + t.item.id);
        setWakeAlarm(am, t.at, pi);
    }

    /**
     * RTC_WAKEUP = يوقظ المعالج من السكون. setExactAndAllowWhileIdle = يعمل حتى في وضع Doze.
     * إذا لم تُمنح صلاحية المنبّهات الدقيقة (أندرويد 12+) نستخدم منبّهاً غير دقيق يوقظ الجهاز أيضاً.
     */
    private static void setWakeAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // أدوات
    // ------------------------------------------------------------------

    private static List<ReminderPlanner.Trigger> plan(SharedPreferences sp) {
        int defaultDays = sp.getInt(KEY_DAYS, 2);
        List<ReminderPlanner.Item> items = new ArrayList<ReminderPlanner.Item>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String date = o.optString("date", "");
                if (id.length() == 0 || date.length() == 0) continue;
                items.add(new ReminderPlanner.Item(
                        id,
                        o.optString("type", ""),
                        o.optString("name", ""),
                        date,
                        o.optInt("daysBefore", defaultDays)));
            }
        } catch (Throwable ignored) {
        }
        return ReminderPlanner.build(items, parseRepeats(sp.getString(KEY_REPEATS, "[]")),
                TimeZone.getDefault());
    }

    private static int[] parseRepeats(String json) {
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            int[] out = new int[arr.length()];
            for (int i = 0; i < out.length; i++) out[i] = arr.optInt(i, 0);
            return out;
        } catch (Throwable e) {
            return new int[0];
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static AlarmManager alarmManager(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent mainPendingIntent(Context ctx) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(ACTION_ALARM);
        return PendingIntent.getBroadcast(ctx, REQ_MAIN, i, piFlags());
    }

    private static int piFlags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }
}
