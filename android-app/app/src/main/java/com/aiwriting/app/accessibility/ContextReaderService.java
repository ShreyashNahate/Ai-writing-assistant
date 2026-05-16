package com.aiwriting.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.aiwriting.app.keyboard.NotificationContextStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads visible text from other apps so the AI can understand context.
 * E.g. reads last 3 WhatsApp messages before suggesting a reply.
 *
 * IMPORTANT: Must be enabled by user in Settings → Accessibility → AI Writing Assistant
 */
public class ContextReaderService extends AccessibilityService {

    private static final String TAG = "ContextReaderService";
    private static final int MAX_CONTEXT_LENGTH = 500;

    // Singleton store so AIKeyboardService can read the context
    private static String lastScreenContext = "";
    private static String lastPackageName = "";

    public static String getLastScreenContext() {
        return lastScreenContext;
    }

    public static String getLastPackageName() {
        return lastPackageName;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null
                ? event.getPackageName().toString() : "";

        // Update which app is in focus
        lastPackageName = packageName;
        NotificationContextStore.getInstance().setCurrentApp(mapPackageToAppName(packageName));

        int eventType = event.getEventType();

        // Capture text when user enters a text field or window content changes
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String context = extractTextFromNode(rootNode);
                if (!context.isEmpty()) {
                    lastScreenContext = context;
                    Log.d(TAG, "Context captured from " + packageName + ": " +
                            context.substring(0, Math.min(50, context.length())) + "...");
                }
                rootNode.recycle();
            }
        }
    }

    /**
     * Recursively extracts all visible text from accessibility node tree
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return "";

        StringBuilder sb = new StringBuilder();
        List<String> texts = new ArrayList<>();
        collectTexts(node, texts, 0);

        // Join last few messages (most recent context)
        int start = Math.max(0, texts.size() - 5);
        for (int i = start; i < texts.size(); i++) {
            sb.append(texts.get(i)).append(" ");
        }

        String result = sb.toString().trim();
        return result.length() > MAX_CONTEXT_LENGTH
                ? result.substring(result.length() - MAX_CONTEXT_LENGTH)
                : result;
    }

    private void collectTexts(AccessibilityNodeInfo node, List<String> texts, int depth) {
        if (node == null || depth > 8) return; // Limit tree depth

        CharSequence text = node.getText();
        if (text != null && text.length() > 2) {
            texts.add(text.toString().trim());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectTexts(child, texts, depth + 1);
            if (child != null) child.recycle();
        }
    }

    private String mapPackageToAppName(String packageName) {
        if (packageName.contains("whatsapp")) return "whatsapp";
        if (packageName.contains("gmail")) return "gmail";
        if (packageName.contains("linkedin")) return "linkedin";
        if (packageName.contains("telegram")) return "telegram";
        if (packageName.contains("instagram")) return "instagram";
        if (packageName.contains("slack")) return "slack";
        if (packageName.contains("discord")) return "discord";
        if (packageName.contains("twitter") || packageName.contains("x.com")) return "twitter";
        return "chat";
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        Log.i(TAG, "ContextReaderService connected");
    }
}