package com.aiwriting.app.util;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import com.aiwriting.app.accessibility.ContextReaderService;

import java.util.List;

public class PermissionHelper {

    /**
     * Check if our IME keyboard is currently selected as active input method
     */
    public static boolean isKeyboardEnabled(Context context) {
        String enabledInputMethods = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);
        return enabledInputMethods != null
                && enabledInputMethods.contains(context.getPackageName());
    }

    /**
     * Check if our AccessibilityService is enabled
     */
    public static boolean isAccessibilityEnabled(Context context) {
        AccessibilityManager am =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if notification listener is enabled for our package
     */
    public static boolean isNotificationListenerEnabled(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(context.getPackageName());
    }

    /**
     * Check if overlay permission is granted
     */
    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }
}