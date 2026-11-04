package com.luna.ai

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LunaNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // নিজের অ্যাপের নোটিফিকেশন ইগনোর করবে
        if (sbn.packageName == packageName) return

        // চলমান নোটিফিকেশন (যেমন: মিউজিক প্লেয়ার) ইগনোর করবে
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val finalText = if (bigText.isNotEmpty()) bigText else text

        if (finalText.isNotEmpty() && title.isNotEmpty()) {
            // অ্যাপের নাম বের করা
            val pm = applicationContext.packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                sbn.packageName
            }

            // মেইন অ্যাক্টিভিটি বা ফ্লোটিং সার্ভিসে মেসেজ পাঠানো
            MainActivity.sendMessageToWebView(appName, title, finalText)
        }
    }
}
