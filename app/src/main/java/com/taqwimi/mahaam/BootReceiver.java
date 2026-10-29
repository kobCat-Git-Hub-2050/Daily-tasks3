package com.taqwimi.mahaam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * المنبّهات تُمسح عند إعادة تشغيل الجوال، وتتأثر بتغيير الوقت أو المنطقة الزمنية،
 * ويجب إعادة ضبطها عند تحديث التطبيق أو عند منح صلاحية المنبّهات الدقيقة.
 * هذا المستقبِل يعيد الحساب والضبط من القائمة المحفوظة في كل هذه الحالات.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        if (a == null) return;

        // قائمة بيضاء: لا نعمل إلا على هذه الأحداث (المستقبِل exported لأن بعض الشركات ترسل QUICKBOOT)
        boolean relevant =
                Intent.ACTION_BOOT_COMPLETED.equals(a)
                        || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                        || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a)
                        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                        || Intent.ACTION_TIME_CHANGED.equals(a)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                        || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(a);
        if (!relevant) return;

        try {
            AlarmScheduler.rescheduleAll(context);
        } catch (Throwable ignored) {
        }
    }
}
