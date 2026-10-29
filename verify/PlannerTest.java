package com.taqwimi.mahaam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * اختبار منطق ReminderPlanner على JVM العادية (بدون Android).
 * التشغيل:
 *   javac -d out java/com/taqwimi/mahaam/ReminderPlanner.java test/PlannerTest.java
 *   java -cp out com.taqwimi.mahaam.PlannerTest
 */
public class PlannerTest {

    static int fails = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) fails++;
    }

    static String f(long ms, TimeZone tz) {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        s.setTimeZone(tz);
        return s.format(new java.util.Date(ms));
    }

    static long ms(String s, TimeZone tz) throws Exception {
        SimpleDateFormat p = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        p.setTimeZone(tz);
        return p.parse(s).getTime();
    }

    static List<String> times(List<ReminderPlanner.Trigger> l, TimeZone tz) {
        List<String> out = new ArrayList<String>();
        for (ReminderPlanner.Trigger t : l) out.add(f(t.at, tz));
        return out;
    }

    static ReminderPlanner.Item item(String id, String date, int days) {
        return new ReminderPlanner.Item(id, "مناسبة", "زفاف " + id, date, days);
    }

    public static void main(String[] args) throws Exception {
        TimeZone riyadh = TimeZone.getTimeZone("Asia/Riyadh");
        int[] reps = {12, 6, 4, 1};

        // 1) المواعيد الأساسية: يوم 28، قبل يومين + 12/6/4/1 ساعة
        List<ReminderPlanner.Item> one = Arrays.asList(item("e1", "2026-10-28", 2));
        List<ReminderPlanner.Trigger> all = ReminderPlanner.build(one, reps, riyadh);
        check("1. عدد التنبيهات = 5", all.size() == 5);
        check("1. المواعيد صحيحة ومرتبة",
                times(all, riyadh).equals(Arrays.asList(
                        "2026-10-26 09:00", "2026-10-27 12:00", "2026-10-27 18:00",
                        "2026-10-27 20:00", "2026-10-27 23:00")));

        // 2) قبل 0 يوم = صباح يوم المناسبة نفسه
        List<ReminderPlanner.Trigger> z = ReminderPlanner.build(
                Arrays.asList(item("z", "2026-10-28", 0)), new int[0], riyadh);
        check("2. قبل 0 يوم = 2026-10-28 09:00", times(z, riyadh).equals(Arrays.asList("2026-10-28 09:00")));

        // 3) تواريخ غير صالحة تُتجاهل دون انهيار
        List<ReminderPlanner.Item> bad = Arrays.asList(
                item("b1", "2026-02-30", 1), item("b2", "abc", 1), item("b3", "2026-13-01", 1),
                item("b4", "", 1), item("b5", "2026-10", 1), item("ok", "2026-10-28", 1));
        List<ReminderPlanner.Trigger> badAll = ReminderPlanner.build(bad, new int[]{1}, riyadh);
        check("3. التواريخ الخاطئة تُتجاهل (يبقى تنبيهان للمناسبة السليمة)", badAll.size() == 2);

        // 4) تكرار نفس عدد الساعات لا يُنتج تنبيهات مكررة
        List<ReminderPlanner.Trigger> dup = ReminderPlanner.build(one, new int[]{6, 6, 12, 0, -3}, riyadh);
        check("4. حذف المكرر والقيم غير الصالحة (1 + 2 = 3)", dup.size() == 3);

        // 5) محاكاة سلسلة المنبّه الواحد المتجدد لمناسبتين: كل تنبيه يصدر مرة واحدة بالترتيب
        List<ReminderPlanner.Item> two = Arrays.asList(
                item("e1", "2026-10-28", 2), item("e2", "2026-10-29", 2));
        List<ReminderPlanner.Trigger> all2 = ReminderPlanner.build(two, reps, riyadh);
        long last = ms("2026-10-25 10:00", riyadh);
        List<String> fired = new ArrayList<String>();
        int guard = 0;
        while (guard++ < 100) {
            ReminderPlanner.Trigger t = ReminderPlanner.next(all2, last);
            if (t == null) break;
            long now = t.at + 700; // الجهاز يتأخر أقل من ثانية
            List<ReminderPlanner.Trigger> due = ReminderPlanner.due(all2, last, now + 2000, now);
            for (ReminderPlanner.Trigger d : due) fired.add(d.item.id + "@" + f(d.at, riyadh));
            last = now + 2000;
        }
        check("5. أُطلق 10 تنبيهات بالضبط (5 لكل مناسبة) بلا تكرار", fired.size() == 10
                && new java.util.HashSet<String>(fired).size() == 10);
        check("5. التنبيه الأول e1@2026-10-26 09:00", fired.get(0).equals("e1@2026-10-26 09:00"));
        check("5. التنبيه الأخير e2@2026-10-28 23:00", fired.get(9).equals("e2@2026-10-28 23:00"));

        // 6) الجهاز كان مغلقاً: عند التشغيل يصل تنبيه واحد (الأحدث) لكل مناسبة
        long from = ms("2026-10-27 08:00", riyadh);
        long nowCatch = ms("2026-10-27 21:00", riyadh);
        List<ReminderPlanner.Trigger> catchUp = ReminderPlanner.due(all2, from, nowCatch, nowCatch);
        check("6. تعويض الفائت: تنبيه لكل مناسبة (2)", catchUp.size() == 2);
        boolean e1Latest = false;
        for (ReminderPlanner.Trigger t : catchUp) {
            if (t.item.id.equals("e1") && f(t.at, riyadh).equals("2026-10-27 20:00") && t.amount == 4) e1Latest = true;
        }
        check("6. للمناسبة e1 يُختار الأحدث (قبل 4 ساعات)", e1Latest);

        // 7) المناسبة التي انتهى يومها لا يصلها تنبيه فائت
        long nowLate = ms("2026-10-29 00:00", riyadh);
        List<ReminderPlanner.Trigger> ended = ReminderPlanner.due(all2, ms("2026-10-27 08:00", riyadh), nowLate, nowLate);
        boolean e1Present = false;
        for (ReminderPlanner.Trigger t : ended) if (t.item.id.equals("e1")) e1Present = true;
        check("7. مناسبة منتهية لا تُنبَّه", !e1Present);

        // 8) نص الإشعار
        ReminderPlanner.Trigger firstDay = all.get(0);
        check("8. نص تنبيه الأيام",
                ReminderPlanner.body(firstDay, firstDay.at).equals("مناسبة – زفاف e1\n2026-10-28 · بعد يومين"));
        ReminderPlanner.Trigger h6 = all.get(2);
        check("8. نص تنبيه الساعات",
                ReminderPlanner.body(h6, h6.at).equals("مناسبة – زفاف e1\n2026-10-28 · بعد 6 ساعات"));
        check("8. غداً", ReminderPlanner.whenPhrase(
                ReminderPlanner.build(Arrays.asList(item("x", "2026-10-28", 1)), new int[0], riyadh).get(0), 0)
                .equals("غداً"));
        check("8. اليوم", ReminderPlanner.whenPhrase(z.get(0), 0).equals("اليوم"));

        // 9) التوقيت الصيفي (نيويورك): الساعة 09:00 المحلية تبقى ثابتة عبر تغيّر التوقيت
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        List<ReminderPlanner.Trigger> dst = ReminderPlanner.build(
                Arrays.asList(item("d", "2026-11-03", 2)), new int[]{12}, ny);
        check("9. DST: قبل يومين = 2026-11-01 09:00 محلياً", f(dst.get(0).at, ny).equals("2026-11-01 09:00"));
        long start = ReminderPlanner.at("2026-11-03", 0, 0, ny);
        check("9. DST: قبل 12 ساعة = بداية المناسبة − 12 ساعة بالضبط", dst.get(1).at == start - 12L * 3600000L);

        // 10) لغة الجهاز بتقويم بوذي لا تُفسد الحساب
        Locale saved = Locale.getDefault();
        Locale.setDefault(new Locale("th", "TH", "TH"));
        List<ReminderPlanner.Trigger> th = ReminderPlanner.build(one, reps, riyadh);
        Locale.setDefault(saved);
        check("10. لغة ثاي/بوذي: نفس النتائج", times(th, riyadh).equals(times(all, riyadh)));

        System.out.println();
        System.out.println(fails == 0 ? "كل الاختبارات نجحت" : ("فشل " + fails + " اختبار"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
