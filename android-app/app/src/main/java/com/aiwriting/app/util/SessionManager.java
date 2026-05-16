package com.aiwriting.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores session data locally: cached token, uid, preferred tone, pro status.
 * Token is always refreshed from Firebase — this is just a local cache for UI.
 */
public class SessionManager {

    private static final String PREF_NAME = "ai_writing_session";
    private static final String KEY_UID = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_IS_PRO = "is_pro";
    private static final String KEY_PREFERRED_TONE = "preferred_tone";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_KEYBOARD_ENABLED = "keyboard_enabled";

    private final SharedPreferences prefs;
    private static SessionManager instance;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveUser(String uid, String email, String displayName, boolean isPro) {
        prefs.edit()
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putBoolean(KEY_IS_PRO, isPro)
                .apply();
    }

    public String getUid() { return prefs.getString(KEY_UID, null); }
    public String getEmail() { return prefs.getString(KEY_EMAIL, null); }
    public String getDisplayName() { return prefs.getString(KEY_DISPLAY_NAME, ""); }
    public boolean isPro() { return prefs.getBoolean(KEY_IS_PRO, false); }

    public String getPreferredTone() {
        return prefs.getString(KEY_PREFERRED_TONE, "professional");
    }
    public void setPreferredTone(String tone) {
        prefs.edit().putString(KEY_PREFERRED_TONE, tone).apply();
    }

    public boolean isOverlayEnabled() {
        return prefs.getBoolean(KEY_OVERLAY_ENABLED, false);
    }
    public void setOverlayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
    }

    public boolean isKeyboardEnabled() {
        return prefs.getBoolean(KEY_KEYBOARD_ENABLED, false);
    }
    public void setKeyboardEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEYBOARD_ENABLED, enabled).apply();
    }

    public boolean isLoggedIn() {
        return getUid() != null;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}