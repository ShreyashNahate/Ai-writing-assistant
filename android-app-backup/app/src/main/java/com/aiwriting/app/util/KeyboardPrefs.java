package com.aiwriting.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the user's last selected tone and action in the AI keyboard.
 * So "casual + shorten" stays selected across sessions.
 */
public class KeyboardPrefs {

    private static final String PREF = "keyboard_prefs";
    private static final String KEY_TONE   = "selected_tone";
    private static final String KEY_ACTION = "selected_action";

    private final SharedPreferences prefs;
    private static KeyboardPrefs instance;

    private KeyboardPrefs(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized KeyboardPrefs getInstance(Context ctx) {
        if (instance == null) instance = new KeyboardPrefs(ctx);
        return instance;
    }

    public String getTone()   { return prefs.getString(KEY_TONE,   "professional"); }
    public String getAction() { return prefs.getString(KEY_ACTION, "rewrite"); }

    public void saveTone(String tone)     { prefs.edit().putString(KEY_TONE,   tone).apply(); }
    public void saveAction(String action) { prefs.edit().putString(KEY_ACTION, action).apply(); }
}