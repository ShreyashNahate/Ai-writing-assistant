package com.aiwriting.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aiwriting.app.keyboard.NotificationContextStore;

import java.util.ArrayList;
import java.util.List;

public class ContextReaderService extends AccessibilityService {

    private static final String TAG = "ContextReader";

    public static final String ACTION_SHOW_BUBBLE = "com.aiwriting.SHOW_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "com.aiwriting.HIDE_BUBBLE";

    public static volatile boolean isPanelOpen = false;
    private static ContextReaderService instance;

    private static String lastOpponentMessages = "";
    private static String currentEditableText = "";
    private static String lastPackageName = ""; // last REAL app (not systemui/our app)

    // Packages we should NEVER read from
    private static final List<String> IGNORED_PACKAGES = List.of(
            "com.aiwriting.app", // our own app
            "com.android.systemui", // Android system UI
            "com.android.launcher", // home screen
            "com.vivo.launcher", // Vivo launcher
            "com.miui.home", // Xiaomi launcher
            "com.google.android.inputmethod" // gboard
    );

    public static String getLastConversationContext() {
        return lastOpponentMessages;
    }

    public static String getCurrentEditableText() {
        return currentEditableText;
    }

    public static String getLastPackageName() {
        return lastPackageName;
    }

    public static String getLastScreenContext() {
        return lastOpponentMessages;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 150;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        Log.i(TAG, "Connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || isPanelOpen)
            return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // FIX 1: Ignore our own app and system UI — never read from them
        if (isIgnoredPackage(pkg))
            return;

        // Only update lastPackageName with real chat/email apps
        lastPackageName = pkg;
        NotificationContextStore.getInstance().setCurrentApp(mapApp(pkg));

        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // What user is typing
                String editable = findEditableText(root);
                if (editable != null)
                    currentEditableText = editable;
                Log.d("AI_READ", "App: " + pkg);
                Log.d("AI_READ", "Typing field: [" + currentEditableText + "]");
                // Opponent messages
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                List<String> opponentMsgs = collectOpponentMessages(root, screenWidth);
                Log.d("AI_READ", "Opponent msgs found: " + opponentMsgs);
                if (!opponentMsgs.isEmpty()) {
                    int from = Math.max(0, opponentMsgs.size() - 2);
                    StringBuilder sb = new StringBuilder();
                    for (int i = from; i < opponentMsgs.size(); i++)
                        sb.append(opponentMsgs.get(i)).append("\n");
                    lastOpponentMessages = sb.toString().trim();
                    Log.d(TAG, "Opponent context: " + lastOpponentMessages);
                }
                root.recycle();
            }

            if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                AccessibilityNodeInfo src = event.getSource();
                if (src != null) {
                    if (src.isEditable())
                        broadcast(ACTION_SHOW_BUBBLE);
                    src.recycle();
                }
            }
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            broadcast(ACTION_HIDE_BUBBLE);
        }
    }

    private boolean isIgnoredPackage(String pkg) {
        for (String ignored : IGNORED_PACKAGES) {
            if (pkg.startsWith(ignored))
                return true;
        }
        return false;
    }

    // ── What user is typing ─────────────────────────────────────
    private String findEditableText(AccessibilityNodeInfo root) {
        if (root == null)
            return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            CharSequence text = focused.getText();
            focused.recycle();
            return text != null ? text.toString().trim() : "";
        }
        return findEditableRecursive(root);
    }

    private String findEditableRecursive(AccessibilityNodeInfo node) {
        if (node == null)
            return null;
        if (node.isEditable()) {
            CharSequence t = node.getText();
            return t != null ? t.toString().trim() : "";
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            String r = findEditableRecursive(child);
            if (child != null)
                child.recycle();
            if (r != null)
                return r;
        }
        return null;
    }

    // ── Opponent messages (left-aligned, filtered) ──────────────
    private List<String> collectOpponentMessages(AccessibilityNodeInfo root, int screenWidth) {
        List<String> result = new ArrayList<>();
        collectOpponentRecursive(root, result, screenWidth, 0);
        return result;
    }

    private void collectOpponentRecursive(AccessibilityNodeInfo node,
            List<String> result, int screenWidth, int depth) {
        if (node == null || depth > 12)
            return;

        if (!node.isEditable()) {
            CharSequence text = node.getText();
            if (text != null) {
                String s = text.toString().trim();
                if (isValidMessage(s)) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    boolean isLeftAligned = bounds.left < (screenWidth * 0.55f);
                    boolean hasWidth = (bounds.right - bounds.left) > 80;
                    if (isLeftAligned && hasWidth) {
                        result.add(s);
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectOpponentRecursive(child, result, screenWidth, depth + 1);
            if (child != null)
                child.recycle();
        }
    }

    private boolean isValidMessage(String s) {
        if (s.length() < 5)
            return false;

        // FIX 2: Reject timestamps and dates
        // e.g. "1:47 AM", "May 9, 2026", "Today", "Yesterday", "12:30"
        if (s.matches("^\\d{1,2}:\\d{2}.*"))
            return false; // time: 1:47 AM
        if (s.matches("^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec).*\\d{4}$"))
            return false; // date: May 9, 2026
        if (s.toLowerCase().matches("^(today|yesterday).*"))
            return false;
        if (s.matches("^[0-9:.,\\-/\\s]+$"))
            return false; // pure numbers/symbols
        if (s.matches("^[0-9]+$"))
            return false; // pure number

        // Reject single-word short strings (UI labels, emoji-only)
        if (s.split("\\s+").length < 2 && s.length() < 15)
            return false;

        return true;
    }

    // ── Paste into focused field ────────────────────────────────
    public static void pasteIntoFocusedField(String text) {
        if (instance == null) {
            Log.w(TAG, "Service not connected");
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) instance.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null)
                cm.setPrimaryClip(ClipData.newPlainText("AI", text));

            AccessibilityNodeInfo root = instance.getRootInActiveWindow();
            if (root == null) {
                instance.performGlobalAction(19);
                return;
            }

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.isEditable()) {
                Bundle args = new Bundle();
                args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                focused.recycle();
                root.recycle();
                if (ok)
                    return;
            } else {
                if (focused != null)
                    focused.recycle();
                root.recycle();
            }
            instance.performGlobalAction(19); // GLOBAL_ACTION_PASTE fallback
        } catch (Exception e) {
            Log.e(TAG, "paste error: " + e.getMessage());
        }
    }

    private String mapApp(String pkg) {
        if (pkg.contains("whatsapp"))
            return "whatsapp";
        if (pkg.contains("gmail"))
            return "gmail";
        if (pkg.contains("telegram"))
            return "telegram";
        if (pkg.contains("instagram"))
            return "instagram";
        if (pkg.contains("slack"))
            return "slack";
        if (pkg.contains("discord"))
            return "discord";
        if (pkg.contains("linkedin"))
            return "linkedin";
        return "chat";
    }

    private void broadcast(String action) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
