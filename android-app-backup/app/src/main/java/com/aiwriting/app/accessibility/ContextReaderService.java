package com.aiwriting.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aiwriting.app.keyboard.NotificationContextStore;

import java.util.ArrayList;
import java.util.List;

public class ContextReaderService extends AccessibilityService {

    private static final String TAG = "AI_DEBUG";

    public static final String ACTION_SHOW_BUBBLE = "com.aiwriting.app.SHOW_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "com.aiwriting.app.HIDE_BUBBLE";

    // Set by FloatingOverlayService when panel is open — freezes context capture
    public static boolean isPanelOpen = false;

    private static String lastScreenContext = "";
    private static String lastPackageName   = "";
    private static String lastAppName       = "";

    // Saved reference to focused editable node — used for direct paste
    private static AccessibilityNodeInfo focusedEditableNode = null;

    // Debounce handler
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable contextDebounce;
    private long lastEventTime = 0;

    // Packages to completely ignore
    private static final String[] IGNORE_PACKAGES = {
            "com.aiwriting.app",
            "com.android.systemui",
            "com.android.launcher3",
            "com.vivo.launcher",
            "com.vivo.globalanimation",
            "com.vivo.upslide",
    };

    // Packages that contain these strings are also ignored
    private static final String[] IGNORE_CONTAINS = {
            "inputmethod", "keyboard", "launcher", "animation",
            "systemui", "upslide"
    };

    public static String getLastScreenContext() { return lastScreenContext; }
    public static String getLastPackageName()   { return lastPackageName; }
    public static String getLastAppName()       { return lastAppName; }

    // ── Called by FloatingOverlayService to paste result ─────────
    public static void pasteIntoFocusedField(String text) {
        if (focusedEditableNode == null) {
            Log.e(TAG, "pasteIntoFocusedField: no focused node saved");
            return;
        }
        try {
            // Try ACTION_SET_TEXT (replaces entire field content)
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = focusedEditableNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            Log.e(TAG, "pasteIntoFocusedField ACTION_SET_TEXT success=" + ok);
            if (!ok) {
                // Fallback: clipboard paste
                focusedEditableNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Paste failed: " + e.getMessage());
        }
    }
    private void findFocusedOrFirstEditable(AccessibilityNodeInfo root) {
        // Try to find focused editable first
        AccessibilityNodeInfo focused = root.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            focusedEditableNode = focused;
            return;
        }
        // Fallback: find first editable node
        List<AccessibilityNodeInfo> editables = new ArrayList<>();
        findEditableNodes(root, editables);
        if (!editables.isEmpty()) {
            focusedEditableNode = editables.get(0);
        }
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";

        // ── Ignore system/launcher/keyboard packages ──────────────
        if (shouldIgnorePackage(pkg)) return;

        // ── Global debounce 300ms ─────────────────────────────────
        lastPackageName = pkg;
        lastAppName = mapPackageToAppName(pkg);
        NotificationContextStore.getInstance().setCurrentApp(lastAppName);

        int type = event.getEventType();

// Window state changed — always process, no debounce
// WhatsApp doesn't fire TYPE_VIEW_FOCUSED so we need this
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isKeyboardPackage(pkg)) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                if (hasEditableNode(root)) {
                    findFocusedOrFirstEditable(root);
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_SHOW_BUBBLE));
                    Log.e(TAG, "Editable found on window change in: " + pkg);
                } else {
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_HIDE_BUBBLE));
                    focusedEditableNode = null;
                    Log.e(TAG, "No editable node in window — hiding bubble");
                }
                root.recycle();
            }
            return; // handled, don't fall through
        }

// Debounce only for other events
        long now = System.currentTimeMillis();
        if (now - lastEventTime < 300) return;
        lastEventTime = now;

        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            if (isKeyboardPackage(pkg)) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                if (hasEditableNode(root)) {
                    // Window has editable field — show bubble
                    // This catches WhatsApp which doesn't fire TYPE_VIEW_FOCUSED
                    findFocusedOrFirstEditable(root);
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_SHOW_BUBBLE));
                    Log.e(TAG, "Editable found on window change in: " + pkg);
                } else {
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_HIDE_BUBBLE));
                    focusedEditableNode = null;
                    Log.e(TAG, "No editable node in window — hiding bubble");
                }
                root.recycle();
            }
        }

        // ── Window state changed — check if we left editable screen ─
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Skip keyboard windows — they don't mean we left the chat
            if (isKeyboardPackage(pkg)) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                if (!hasEditableNode(root)) {
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent(ACTION_HIDE_BUBBLE));
                    focusedEditableNode = null;
                    Log.e(TAG, "No editable node in window — hiding bubble");
                }
                root.recycle();
            }
        }

        // ── Content changed — refresh context (debounced) ─────────
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            scheduleContextCapture(pkg, 600);
        }
    }

    private boolean shouldIgnorePackage(String pkg) {
        for (String ignore : IGNORE_PACKAGES) {
            if (pkg.equals(ignore)) return true;
        }
        for (String contains : IGNORE_CONTAINS) {
            if (pkg.contains(contains)) return true;
        }
        return false;
    }

    private boolean isKeyboardPackage(String pkg) {
        return pkg.contains("inputmethod") || pkg.contains("keyboard")
                || pkg.contains("gboard") || pkg.contains("swiftkey")
                || pkg.contains("upslide") || pkg.contains("animation");
    }

    // ── Debounced capture ─────────────────────────────────────────
    private void scheduleContextCapture(String pkg, long delayMs) {
        if (contextDebounce != null) handler.removeCallbacks(contextDebounce);
        contextDebounce = () -> captureScreenContext(pkg);
        handler.postDelayed(contextDebounce, delayMs);
    }

    private void captureScreenContext(String pkg) {
        // Don't overwrite while panel is showing AI result
        if (isPanelOpen) return;
        if (shouldIgnorePackage(pkg)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String context = null;

        try {
            if (pkg.contains("whatsapp") || pkg.contains("telegram")) {
                context = extractMessagingContext(root);
            } else if (pkg.contains("gmail") || pkg.contains("outlook")
                    || pkg.contains("mail")) {
                context = extractEmailContext(root);
            } else {
                // For all other apps — get what's in the editable field
                context = extractEditableFieldText(root);
            }

            // Fallbacks
            if (context == null || context.trim().isEmpty())
                context = extractEditableFieldText(root);
            if (context == null || context.trim().isEmpty())
                context = extractGeneralText(root);

        } finally {
            root.recycle();
        }

        if (context != null && !context.trim().isEmpty()) {
            String cleaned = cleanText(context);
            // Don't save placeholder text
            if (!isPlaceholder(cleaned)) {
                lastScreenContext = cleaned;
                Log.e(TAG, "CONTEXT CAPTURED [" + pkg + "]: " + lastScreenContext);
            }
        }
    }

    // ── WhatsApp / Telegram ───────────────────────────────────────
    // Priority: what user typed > last received message
    private String extractMessagingContext(AccessibilityNodeInfo root) {
        // 1. What user is currently typing
        String inputText = extractEditableFieldText(root);
        if (inputText != null && !inputText.trim().isEmpty()
                && !isPlaceholder(inputText.trim())) {
            return inputText.trim();
        }

        // 2. Last received message — for smart reply
        List<String> bubbles = new ArrayList<>();
        collectMessageBubbles(root, bubbles, 0);
        if (!bubbles.isEmpty()) {
            // Only the last message to avoid mixing conversations
            return bubbles.get(bubbles.size() - 1);
        }
        return "";
    }

    // ── Gmail / Outlook ───────────────────────────────────────────
    private String extractEmailContext(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> editables = new ArrayList<>();
        findEditableNodes(root, editables);
        StringBuilder sb = new StringBuilder();
        for (AccessibilityNodeInfo n : editables) {
            CharSequence text = n.getText();
            if (text != null && text.length() > 2 && !isPlaceholder(text.toString())) {
                sb.append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ── Read focused editable field ───────────────────────────────
    private String extractEditableFieldText(AccessibilityNodeInfo root) {
        // Try saved focused node first
        if (focusedEditableNode != null) {
            try {
                CharSequence text = focusedEditableNode.getText();
                if (text != null && text.length() > 2) return text.toString().trim();
            } catch (Exception ignored) {}
        }
        // Search tree for any editable field with content
        List<AccessibilityNodeInfo> editables = new ArrayList<>();
        findEditableNodes(root, editables);
        for (AccessibilityNodeInfo n : editables) {
            CharSequence text = n.getText();
            if (text != null && text.length() > 2) return text.toString().trim();
        }
        return "";
    }

    // ── WhatsApp message bubbles — non-editable multi-word TVs ───
    private void collectMessageBubbles(AccessibilityNodeInfo node,
                                       List<String> bubbles, int depth) {
        if (node == null || depth > 12) return;
        CharSequence text = node.getText();
        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        if (cls.contains("TextView") && !node.isEditable()
                && !node.isClickable() && text != null && text.length() > 3) {
            String t = text.toString().trim();
            // Skip timestamps, single words, UI labels
            if (!t.matches("\\d{1,2}:\\d{2}.*")
                    && t.split("\\s+").length > 1
                    && !isPlaceholder(t)) {
                bubbles.add(t);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectMessageBubbles(child, bubbles, depth + 1);
            if (child != null) child.recycle();
        }
    }

    private void findEditableNodes(AccessibilityNodeInfo node,
                                   List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        if (node.isEditable()) result.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            findEditableNodes(child, result);
        }
    }

    private String extractGeneralText(AccessibilityNodeInfo root) {
        List<String> texts = new ArrayList<>();
        collectTexts(root, texts, 0);
        int start = Math.max(0, texts.size() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < texts.size(); i++) sb.append(texts.get(i)).append(" ");
        String result = sb.toString().trim();
        return result.length() > 500 ? result.substring(result.length() - 500) : result;
    }

    private void collectTexts(AccessibilityNodeInfo node, List<String> texts, int depth) {
        if (node == null || depth > 8) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 3 && !isPlaceholder(text.toString()))
            texts.add(text.toString().trim());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectTexts(child, texts, depth + 1);
            if (child != null) child.recycle();
        }
    }

    private boolean hasEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isEditable()) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            boolean found = hasEditableNode(child);
            if (child != null) child.recycle();
            if (found) return true;
        }
        return false;
    }

    // ── Skip placeholder / hint text ─────────────────────────────
    private boolean isPlaceholder(String text) {
        String lower = text.toLowerCase().trim();
        return lower.equals("message") || lower.equals("enter message")
                || lower.equals("type a message") || lower.equals("search")
                || lower.equals("search local apps") || lower.equals("meta ai")
                || lower.startsWith("type or paste") || lower.startsWith("ask meta")
                || lower.startsWith("search for") || text.trim().isEmpty();
    }

    private String cleanText(String raw) {
        // Remove timestamps like "1:33 AM"
        raw = raw.replaceAll("\\d{1,2}:\\d{2}\\s?(AM|PM)", "");
        // Remove "Yesterday", "Today" date labels
        raw = raw.replaceAll("(?i)\\b(yesterday|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", "");
        // Collapse whitespace
        raw = raw.replaceAll("\\s+", " ").trim();
        return raw;
    }

    private String mapPackageToAppName(String pkg) {
        if (pkg.contains("whatsapp"))  return "whatsapp";
        if (pkg.contains("gmail"))     return "gmail";
        if (pkg.contains("linkedin"))  return "linkedin";
        if (pkg.contains("telegram"))  return "telegram";
        if (pkg.contains("instagram")) return "instagram";
        if (pkg.contains("slack"))     return "slack";
        if (pkg.contains("discord"))   return "discord";
        if (pkg.contains("twitter") || pkg.contains("x.com")) return "twitter";
        if (pkg.contains("mms") || pkg.contains("messaging")) return "sms";
        if (pkg.contains("outlook"))   return "email";
        return "app";
    }

    @Override
    public void onInterrupt() { Log.w(TAG, "AccessibilityService interrupted"); }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.e(TAG, "ACCESSIBILITY SERVICE CONNECTED");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }
}