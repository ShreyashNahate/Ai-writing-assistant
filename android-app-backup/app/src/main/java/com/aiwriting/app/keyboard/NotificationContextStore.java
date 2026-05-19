package com.aiwriting.app.keyboard;

/**
 * Singleton store — bridges ContextReaderService (accessibility) and
 * AIKeyboardService so the keyboard knows which app the user is typing in.
 */
public class NotificationContextStore {

    private static NotificationContextStore instance;
    private String currentApp = "chat";
    private String lastNotificationText = "";

    private NotificationContextStore() {}

    public static synchronized NotificationContextStore getInstance() {
        if (instance == null) {
            instance = new NotificationContextStore();
        }
        return instance;
    }

    public String getCurrentApp() { return currentApp; }
    public void setCurrentApp(String app) { this.currentApp = app; }

    public String getLastNotificationText() { return lastNotificationText; }
    public void setLastNotificationText(String text) { this.lastNotificationText = text; }
}