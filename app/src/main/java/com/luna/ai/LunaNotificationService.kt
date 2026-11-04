package com.luna.ai

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LunaNotificationService : NotificationListenerService() {

    companion object {
        // এই instance ভেরিয়েবলটাই MainActivity খুঁজছিল
        var instance: LunaNotificationService? = null
        val recentMessages = mutableMapOf<String, StatusBarNotification>()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val finalText = if (bigText.isNotEmpty()) bigText else text

        if (finalText.isNotEmpty() && title.isNotEmpty()) {
            val pm = applicationContext.packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                sbn.packageName
            }

            // সাইলেন্ট রিপ্লাইয়ের জন্য সেভ করে রাখা
            recentMessages[title] = sbn
            MainActivity.sendMessageToWebView(appName, title, finalText)
        }
    }

    // নোটিফিকেশন থেকেই সাইলেন্টলি মেসেজ পাঠাবে (অ্যাপ ওপেন হবে না)
    fun sendSilentReply(sender: String, message: String): Boolean {
        val sbn = recentMessages[sender] ?: return false
        val actions = sbn.notification.actions ?: return false
        
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val intent = Intent()
                val bundle = Bundle()
                for (remoteInput in remoteInputs) {
                    bundle.putCharSequence(remoteInput.resultKey, message)
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                try {
                    action.actionIntent.send(applicationContext, 0, intent)
                    return true
                } catch (e: PendingIntent.CanceledException) {
                    return false
                }
            }
        }
        return false
    }
}
