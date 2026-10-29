package com.taqwimi.mahaam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * منطق حساب مواعيد التنبيهات (لا يعتمد على Android إطلاقاً، لذلك يمكن اختباره على JVM).
 *
 * لكل مناسبة يُنتج نوعان من التنبيهات:
 *   1) تنبيه "قبل X يوم": الساعة DAYS_BEFORE_HOUR صباحاً، قبل تاريخ المناسبة بعدد الأيام المحدد.
 *   2) تنبيهات "قبل X ساعة" (التذكيرات المتكررة): قبل بداية يوم المناسبة بعدد الساعات المحدد.
 *
 * المناسبات في التطبيق لها تاريخ فقط بلا ساعة، لذا تُعتبر المناسبة بادئة عند الساعة EVENT_START_HOUR
 * من يومها (الافتراضي 0 = منتصف الليل). مثال: "قبل 12 ساعة" = الظهر في اليوم السابق.
 */
final class ReminderPlanner {

    private ReminderPlanner() {}

    /** الساعة (0-23) التي يصدر فيها تنبيه "قبل X يوم". */
    static final int DAYS_BEFORE_HOUR = 9;

    /** الساعة (0-23) التي تُعتبر بداية المناسبة في يومها، ومنها تُحسب "قبل X ساعة". */
    static final int EVENT_START_HOUR = 0;

    private static final long HOUR_MS = 3600000L;

    /** مناسبة واحدة كما تصل من الواجهة. */
    static final class Item {
        final String id, type, name, date; // date بصيغة yyyy-MM-dd
        final int daysBefore;

        Item(String id, String type, String name, String date, int daysBefore) {
            this.id = id == null ? "" : id;
            this.type = (type == null || type.length() == 0) ? "مناسبة" : type;
            this.name = name == null ? "" : name;
            this.date = date == null ? "" : date;
            this.daysBefore = Math.max(0, daysBefore);
        }
    }

    /** موعد تنبيه واحد. */
    static final class Trigger {
        final long at;          // وقت إطلاق التنبيه (ملّي ثانية)
        final Item item;
        final boolean byHours;  // true = "قبل X ساعة"، false = "قبل X يوم"
        final int amount;       // عدد الساعات أو الأيام
        final long eventStart;  // بداية المناسبة
        final long eventEnd;    // نهاية يوم المناسبة

        Trigger(long at, Item item, boolean byHours, int amount, long eventStart, long eventEnd) {
            this.at = at;
            this.item = item;
            this.byHours = byHours;
            this.amount = amount;
            this.eventStart = eventStart;
            this.eventEnd = eventEnd;
        }
    }

    /**
     * يحوّل التاريخ yyyy-MM-dd إلى لحظة زمنية محلية: الساعة hour في اليوم (التاريخ + dayOffset).
     * يرجع -1 إذا كان التاريخ غير صالح (مثل 2026-02-30).
     * نستخدم GregorianCalendar صراحةً حتى لا تتأثر النتيجة بلغة الجهاز (تقويم بوذي/هجري...).
     */
    static long at(String date, int hour, int dayOffset, TimeZone tz) {
        if (date == null) return -1;
        String[] p = date.trim().split("-");
        if (p.length != 3) return -1;
        int y, m, d;
        try {
            y = Integer.parseInt(p[0].trim());
            m = Integer.parseInt(p[1].trim());
            d = Integer.parseInt(p[2].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        if (y < 1900 || y > 2200 || m < 1 || m > 12 || d < 1 || d > 31) return -1;

        GregorianCalendar c = new GregorianCalendar(tz);
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0); // الظهر: لا يقع أبداً في فجوة التوقيت الصيفي
        if (c.get(GregorianCalendar.YEAR) != y
                || c.get(GregorianCalendar.MONTH) != m - 1
                || c.get(GregorianCalendar.DAY_OF_MONTH) != d) {
            return -1; // تاريخ غير موجود
        }
        c.add(GregorianCalendar.DAY_OF_MONTH, dayOffset);
        c.set(GregorianCalendar.HOUR_OF_DAY, hour);
        c.set(GregorianCalendar.MINUTE, 0);
        c.set(GregorianCalendar.SECOND, 0);
        c.set(GregorianCalendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** يبني كل التنبيهات الممكنة (الماضية والمستقبلية) مرتبة زمنياً. */
    static List<Trigger> build(List<Item> items, int[] repeatHours, TimeZone tz) {
        List<Trigger> out = new ArrayList<Trigger>();

        Set<Integer> hours = new LinkedHashSet<Integer>();
        if (repeatHours != null) {
            for (int h : repeatHours) {
                if (h > 0) hours.add(h);
            }
        }

        for (Item it : items) {
            long start = at(it.date, EVENT_START_HOUR, 0, tz);
            if (start < 0) continue;
            long end = at(it.date, 0, 1, tz);

            long dayTrigger = at(it.date, DAYS_BEFORE_HOUR, -it.daysBefore, tz);
            out.add(new Trigger(dayTrigger, it, false, it.daysBefore, start, end));

            for (int h : hours) {
                out.add(new Trigger(start - h * HOUR_MS, it, true, h, start, end));
            }
        }

        Collections.sort(out, new Comparator<Trigger>() {
            @Override
            public int compare(Trigger a, Trigger b) {
                return Long.compare(a.at, b.at);
            }
        });
        return out;
    }

    /** أقرب تنبيه وقته بعد afterMs، أو null إذا لم يبقَ شيء. */
    static Trigger next(List<Trigger> all, long afterMs) {
        Trigger best = null;
        for (Trigger t : all) {
            if (t.at > afterMs && (best == null || t.at < best.at)) best = t;
        }
        return best;
    }

    /**
     * التنبيهات المستحقة في الفترة (fromExclusive, toInclusive].
     * تُرجع تنبيهاً واحداً فقط لكل مناسبة (الأحدث) حتى لا تتكدس الإشعارات إذا تأخر الجهاز،
     * وتتجاهل المناسبات التي انتهى يومها.
     */
    static List<Trigger> due(List<Trigger> all, long fromExclusive, long toInclusive, long now) {
        Map<String, Trigger> best = new LinkedHashMap<String, Trigger>();
        for (Trigger t : all) {
            if (t.at <= fromExclusive || t.at > toInclusive) continue;
            if (now >= t.eventEnd) continue;
            Trigger cur = best.get(t.item.id);
            if (cur == null || t.at > cur.at) best.put(t.item.id, t);
        }
        List<Trigger> out = new ArrayList<Trigger>(best.values());
        Collections.sort(out, new Comparator<Trigger>() {
            @Override
            public int compare(Trigger a, Trigger b) {
                return Long.compare(a.at, b.at);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------
    // نصوص الإشعار
    // ------------------------------------------------------------------

    static String title() {
        return "تذكير بمناسبة";
    }

    static String body(Trigger t, long now) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.item.type);
        if (t.item.name.length() > 0) sb.append(" – ").append(t.item.name);
        sb.append('\n').append(t.item.date).append(" · ").append(whenPhrase(t, now));
        return sb.toString();
    }

    static String whenPhrase(Trigger t, long now) {
        if (t.byHours) {
            long diff = t.eventStart - now;
            if (diff <= 0) return "اليوم";
            int h = (int) Math.max(1, Math.round(diff / (double) HOUR_MS));
            return "بعد " + hoursPhrase(h);
        }
        int d = t.amount;
        if (d == 0) return "اليوم";
        if (d == 1) return "غداً";
        return "بعد " + daysPhrase(d);
    }

    private static String hoursPhrase(int h) {
        if (h == 1) return "ساعة";
        if (h == 2) return "ساعتين";
        if (h <= 10) return h + " ساعات";
        return h + " ساعة";
    }

    private static String daysPhrase(int d) {
        if (d == 2) return "يومين";
        if (d <= 10) return d + " أيام";
        return d + " يوماً";
    }
}
