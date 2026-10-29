package com.taqwimi.mahaam;

import android.app.*;
import android.content.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class AlarmFlowTest {
    static int fails = 0;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + n); if (!ok) fails++; }

    static class Ctx extends Context {
        final Map<String, Map<String,Object>> store; final AlarmManager am = new AlarmManager();
        final NotificationManager nm = new NotificationManager(); final PowerManager pm = new PowerManager();
        Ctx(Map<String, Map<String,Object>> store) { this.store = store; }
        @Override public Object getSystemService(String n) { return n.equals(ALARM_SERVICE) ? am : n.equals(NOTIFICATION_SERVICE) ? nm : n.equals(POWER_SERVICE) ? pm : null; }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            final Map<String,Object> m = store.computeIfAbsent(name, k -> new HashMap<>());
            return new SharedPreferences() {
                public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
                public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
                public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : (Integer) o; }
                public long getLong(String k, long d) { Object o = m.get(k); return o == null ? d : (Long) o; }
                public Editor edit() { return new Editor() {
                    public Editor putString(String k, String v) { m.put(k, v); return this; }
                    public Editor putBoolean(String k, boolean v) { m.put(k, v); return this; }
                    public Editor putInt(String k, int v) { m.put(k, v); return this; }
                    public Editor putLong(String k, long v) { m.put(k, v); return this; }
                    public boolean commit() { return true; } public void apply() {} }; }
            };
        }
    }

    static TimeZone riyadh = TimeZone.getTimeZone("Asia/Riyadh");
    static long clockNow;
    static long ms(String s, TimeZone tz) throws Exception { SimpleDateFormat p = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US); p.setTimeZone(tz); return p.parse(s).getTime(); }
    static String f(long t, TimeZone tz) { SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US); s.setTimeZone(tz); return s.format(new Date(t)); }
    static Intent alarmIntent(String a) { return new Intent(a); }
    static long mainAlarmAt(Ctx c) { for (Map.Entry<PendingIntent,long[]> e : c.am.alarms.entrySet()) if (AlarmScheduler.ACTION_ALARM.equals(e.getKey().action)) return e.getValue()[1]; return -1; }

    static final String LIST2 = "[{\"id\":\"e1\",\"type\":\"زواج\",\"name\":\"فلان \\\"الكبير\\\"\",\"desc\":\"\",\"date\":\"2026-10-28\",\"daysBefore\":2},"
            + "{\"id\":\"e2\",\"type\":\"\",\"name\":\"\",\"desc\":\"x\",\"date\":\"2026-10-29\",\"daysBefore\":2}]";
    static final String LIST_E2 = "[{\"id\":\"e2\",\"type\":\"مناسبة\",\"name\":\"\",\"date\":\"2026-10-29\",\"daysBefore\":2}]";

    /** يشغّل السلسلة حتى لا يبقى منبّه، ويرجع الإشعارات المنشورة */
    static void runChain(Ctx c, List<String> log) throws Exception {
        for (int g = 0; g < 100; g++) {
            long at = mainAlarmAt(c); if (at < 0) return;
            check_once(c.am.alarms.size() <= 1, "منبّه رئيسي واحد على الأكثر");
            clockNow = at + 400; int before = c.nm.posted.size();
            new AlarmReceiver().onReceive(c, alarmIntent(AlarmScheduler.ACTION_ALARM));
            for (int i = before; i < c.nm.posted.size(); i++) { Notification n = (Notification) c.nm.posted.get(i)[1]; log.add(f(at, riyadh) + " | " + n.text.replace('\n', '/')); }
        }
    }
    static boolean multiOk = true; static void check_once(boolean ok, String n) { if (!ok && multiOk) { multiOk = false; check(n, false); } }

    public static void main(String[] a) throws Exception {
        TimeZone.setDefault(riyadh);
        AlarmScheduler.clock = new AlarmScheduler.Clock() { public long now() { return clockNow; } };
        Build.VERSION.SDK_INT = 34;

        // ===== A: السلسلة الكاملة لمناسبتين =====
        clockNow = ms("2026-10-25 10:00", riyadh);
        Map<String, Map<String,Object>> disk = new HashMap<>();
        Ctx c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        check("A1. منبّه واحد فقط بعد الضبط", c.am.alarms.size() == 1);
        check("A2. أول موعد 2026-10-26 09:00", f(mainAlarmAt(c), riyadh).equals("2026-10-26 09:00"));
        long[] v = c.am.alarms.values().iterator().next();
        check("A3. النوع RTC_WAKEUP (يوقظ الجهاز)", v[0] == AlarmManager.RTC_WAKEUP);
        check("A4. الطريقة setExactAndAllowWhileIdle (تعمل في Doze)", c.am.methods.values().iterator().next().equals("setExactAndAllowWhileIdle"));
        List<String> log = new ArrayList<>(); runChain(c, log);
        log.forEach(s -> System.out.println("      " + s));
        check("A5. وصل 10 إشعارات (5 لكل مناسبة)", c.nm.posted.size() == 10);
        check("A6. نص التنبيه الأول صحيح", ((Notification) c.nm.posted.get(0)[1]).text.equals("زواج – فلان \"الكبير\"\n2026-10-28 · بعد يومين"));
        check("A7. القناة v2 عالية الأهمية + اهتزاز + صوت", c.nm.channels.get("taqwimi_reminders_v2") != null && c.nm.channels.get("taqwimi_reminders_v2").importance == 4 && c.nm.channels.get("taqwimi_reminders_v2").vibration && c.nm.channels.get("taqwimi_reminders_v2").hasSound);
        check("A8. أُضيئت الشاشة 10 مرات (مرة لكل انطلاق) والجهاز نائم", c.pm.acquires == 10);
        check("A9. لا يبقى منبّه بعد آخر تنبيه", c.am.alarms.isEmpty());

        // ===== B: حذف مناسبة بعد ضبط منبّهاتها =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        long at = mainAlarmAt(c); clockNow = at + 400; new AlarmReceiver().onReceive(c, alarmIntent(AlarmScheduler.ACTION_ALARM)); // e1 يوم-قبل
        clockNow += 5 * 60000; AlarmScheduler.scheduleFromJson(c, LIST_E2, true, 2, "[12,6,4,1]"); // حذف e1
        check("B1. بعد حذف e1 المنبّه التالي = أول تنبيه لـ e2 (2026-10-27 09:00)", f(mainAlarmAt(c), riyadh).equals("2026-10-27 09:00"));
        int before = c.nm.posted.size(); log = new ArrayList<>(); runChain(c, log);
        boolean e1Late = false; for (int i = before; i < c.nm.posted.size(); i++) if (((Notification) c.nm.posted.get(i)[1]).text.contains("فلان")) e1Late = true;
        check("B2. لا يصل أي تنبيه للمناسبة المحذوفة (كان خللاً في الكود الأصلي)", !e1Late);
        check("B3. وصلت 5 تنبيهات لـ e2", c.nm.posted.size() - before == 5);

        // ===== C: تعطيل التنبيهات =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        AlarmScheduler.scheduleFromJson(c, LIST2, false, 2, "[12,6,4,1]");
        check("C1. التعطيل يلغي المنبّه", c.am.alarms.isEmpty());
        clockNow = ms("2026-10-26 09:00", riyadh); new AlarmReceiver().onReceive(c, alarmIntent(AlarmScheduler.ACTION_ALARM));
        check("C2. منبّه متأخر بعد التعطيل لا يعرض شيئاً", c.nm.posted.isEmpty());

        // ===== D: إعادة تشغيل الجوال بعد فوات تنبيهات =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        Ctx rebooted = new Ctx(disk); // الذاكرة تبقى، المنبّهات تُمسح
        check("D0. بعد الإقلاع لا منبّهات", rebooted.am.alarms.isEmpty());
        clockNow = ms("2026-10-27 21:00", riyadh);
        Intent boot = new Intent(Intent.ACTION_BOOT_COMPLETED); new BootReceiver().onReceive(rebooted, boot);
        check("D1. تعويض الفائت: إشعاران (واحد لكل مناسبة)", rebooted.nm.posted.size() == 2);
        check("D2. أُعيد ضبط المنبّه التالي = 2026-10-27 23:00", f(mainAlarmAt(rebooted), riyadh).equals("2026-10-27 23:00"));
        new BootReceiver().onReceive(rebooted, new Intent("android.intent.action.SCREEN_ON"));
        check("D3. حدث غير ذي صلة لا يفعل شيئاً", rebooted.nm.posted.size() == 2);
        new BootReceiver().onReceive(rebooted, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        check("D4. تحديث التطبيق لا يكرر الإشعارات", rebooted.nm.posted.size() == 2 && f(mainAlarmAt(rebooted), riyadh).equals("2026-10-27 23:00"));

        // ===== E: تغيير المنطقة الزمنية =====
        TimeZone tokyo = TimeZone.getTimeZone("Asia/Tokyo"); TimeZone.setDefault(tokyo);
        new BootReceiver().onReceive(rebooted, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
        check("E1. أُعيد حساب المنبّه بتوقيت المنطقة الجديدة (2026-10-28 12:00 بتوقيت طوكيو)", f(mainAlarmAt(rebooted), tokyo).equals("2026-10-28 12:00"));
        check("E2. تغيير المنطقة الزمنية لا يكرر إشعارات سبق عرضها", rebooted.nm.posted.size() == 2);
        TimeZone.setDefault(riyadh);

        // ===== F: 800 مناسبة (حد 500 منبّه في أندرويد 12+) =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        StringBuilder sb = new StringBuilder("["); Calendar cal = new GregorianCalendar(riyadh); cal.setTimeInMillis(clockNow);
        for (int i = 0; i < 800; i++) { cal.add(Calendar.DAY_OF_MONTH, i % 3 == 0 ? 1 : 0); if (i > 0) sb.append(','); sb.append("{\"id\":\"x" + i + "\",\"type\":\"t\",\"name\":\"n\",\"date\":\"" + new SimpleDateFormat("yyyy-MM-dd", Locale.US) { { setTimeZone(riyadh); } }.format(cal.getTime()) + "\",\"daysBefore\":2}"); }
        sb.append(']');
        AlarmScheduler.scheduleFromJson(c, sb.toString(), true, 2, "[12,6,4,1]");
        check("F1. 800 مناسبة × 5 تنبيهات = 4000 موعد، لكن منبّه واحد فقط مسجّل", c.am.alarms.size() == 1);

        // ===== G: الصلاحيات وإصدارات أندرويد =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk); c.am.exactAllowed = false;
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12]");
        check("G1. أندرويد 12+ بلا صلاحية دقيقة: منبّه غير دقيق لكنه يوقظ (setAndAllowWhileIdle)", c.am.methods.values().iterator().next().equals("setAndAllowWhileIdle"));
        Build.VERSION.SDK_INT = 22; disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12]");
        check("G2. أندرويد 5.1: setExact", c.am.methods.values().iterator().next().equals("setExact"));
        Build.VERSION.SDK_INT = 25; new AlarmReceiver().onReceive(c, alarmIntent(AlarmScheduler.ACTION_TEST));
        Notification t25 = (Notification) c.nm.posted.get(c.nm.posted.size() - 1)[1];
        check("G3. أندرويد 7: الإشعار بأولوية عالية + صوت واهتزاز افتراضي (كان صامتاً)", t25.priority == 1 && t25.defaults == -1 && t25.channelId == null && c.nm.channels.isEmpty());
        Build.VERSION.SDK_INT = 34;

        // ===== H: واجهة قديمة بثلاث وسائط تُبقي التذكيرات المتكررة =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2); // 4 وسائط (بلا repeats)
        List<String> l2 = new ArrayList<>(); runChain(c, l2);
        check("H1. إبقاء المحفوظ: ما زالت 10 تنبيهات", c.nm.posted.size() == 10);

        // ===== I: اختبار المنبّه التجريبي =====
        clockNow = ms("2026-10-25 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[]");
        AlarmScheduler.scheduleTest(c, 60);
        check("I1. المنبّه التجريبي منفصل عن الرئيسي (2 مسجّلان)", c.am.alarms.size() == 2);
        new AlarmReceiver().onReceive(c, alarmIntent(AlarmScheduler.ACTION_TEST));
        check("I2. وصل إشعار الاختبار وأُضيئت الشاشة", c.nm.posted.size() == 1 && c.pm.acquires == 1);

        // ===== J: تعديل المستخدم لا يُطلق تنبيهات فائتة =====
        clockNow = ms("2026-10-27 10:00", riyadh); disk.clear(); c = new Ctx(disk);
        AlarmScheduler.scheduleFromJson(c, LIST2, true, 2, "[12,6,4,1]");
        check("J1. إضافة مناسبة قريبة لا تُطلق فوراً تنبيهات فات وقتها", c.nm.posted.isEmpty() && f(mainAlarmAt(c), riyadh).equals("2026-10-27 12:00"));

        System.out.println(); System.out.println(fails == 0 ? "كل اختبارات المحاكاة نجحت" : ("فشل " + fails)); System.exit(fails == 0 ? 0 : 1);
    }
}
