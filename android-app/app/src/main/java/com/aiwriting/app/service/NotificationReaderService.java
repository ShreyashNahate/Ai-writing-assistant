package com.aiwriting.app.service;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.aiwriting.app.keyboard.NotificationContextStore;

/**
 * Listens to incoming notifications from WhatsApp, Gmail, Telegram etc.
 * Extracts sender + message text → stores in NotificationContextStore
 * so AIKeyboardService can send it as context to Groq API.
 *
 * Must be enabled by user: Settings → Apps → Special App Access → Notification Access
 */
public class NotificationReaderService extends NotificationListenerService {

    private static final String TAG = "NotificationReader";

    // Apps we care about for context
    private static final String PKG_WHATSAPP  = "com.whatsapp";
    private static final String PKG_TELEGRAM  = "org.telegram.messenger";
    private static final String PKG_GMAIL     = "com.google.android.gm";
    private static final String PKG_INSTAGRAM = "com.instagram.android";
    private static final String PKG_SLACK     = "com.Slack";
    private static final String PKG_DISCORD   = "com.discord";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();
        if (!isRelevantApp(packageName)) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        String body = text != null ? text.toString() : "";

        if (!body.isEmpty()) {
            String context = (title.isEmpty() ? "" : title + ": ") + body;
            NotificationContextStore.getInstance().setLastNotificationText(context);
            NotificationContextStore.getInstance().setCurrentApp(mapToAppName(packageName));

            Log.d(TAG, "Notification captured from " + packageName
                    + " | " + context.substring(0, Math.min(60, context.length())));
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // No-op — we don't need to clear on removal
    }

    private boolean isRelevantApp(String packageName) {
        return packageName.equals(PKG_WHATSAPP)  ||
                packageName.equals(PKG_TELEGRAM)  ||
                packageName.equals(PKG_GMAIL)     ||
                packageName.equals(PKG_INSTAGRAM) ||
                packageName.equals(PKG_SLACK)     ||
                packageName.equals(PKG_DISCORD);
    }

    private String mapToAppName(String packageName) {
        switch (packageName) {
            case PKG_WHATSAPP:  return "whatsapp";
            case PKG_TELEGRAM:  return "telegram";
            case PKG_GMAIL:     return "gmail";
            case PKG_INSTAGRAM: return "instagram";
            case PKG_SLACK:     return "slack";
            case PKG_DISCORD:   return "discord";
            default:            return "chat";
        }
    }
}