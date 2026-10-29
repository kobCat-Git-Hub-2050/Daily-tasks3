package com.taqwimi.mahaam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * يستقبل المنبّه (حتى لو كان التطبيق مغلقاً أو الجهاز نائماً).
 * النظام يُبقي الجهاز مستيقظاً طوال تنفيذ onReceive، والعمل هنا سريع (حساب + إشعار).
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent == null ? null : intent.getAction();
            if (AlarmScheduler.ACTION_TEST.equals(action)) {
                AlarmScheduler.showTestNotification(context);
                return;
            }
            AlarmScheduler.onAlarmFired(context);
        } catch (Throwable ignored) {
            // مستقبِل البث يجب ألا يُسقط التطبيق أبداً
        }
    }
}
