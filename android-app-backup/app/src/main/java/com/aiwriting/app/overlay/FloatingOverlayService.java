package com.aiwriting.app.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aiwriting.app.AIWritingApp;
import com.aiwriting.app.R;
import com.aiwriting.app.accessibility.ContextReaderService;
import com.aiwriting.app.api.BackendApiService;
import com.aiwriting.app.keyboard.NotificationContextStore;
import com.aiwriting.app.model.RewriteRequest;
import com.aiwriting.app.model.RewriteResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FloatingOverlayService extends Service {

    private static final String TAG            = "AI_DEBUG";
    private static final String CHANNEL_ID     = "ai_overlay_channel";
    private static final int    NOTIF_ID       = 1001;

    private WindowManager windowManager;
    private View bubbleView;
    private View panelView;
    private BackendApiService apiService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    private boolean bubbleAdded = false;
    private boolean panelAdded  = false;
    private boolean isPanelOpen = false;

    private int   initialX, initialY;
    private float initialTouchX, initialTouchY;

    // ── Broadcast receiver ────────────────────────────────────────
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            Log.e(TAG, "BROADCAST RECEIVED: " + intent.getAction());
            if (intent.getAction().equals(ContextReaderService.ACTION_SHOW_BUBBLE))
                handler.post(FloatingOverlayService.this::showBubble);
            else if (intent.getAction().equals(ContextReaderService.ACTION_HIDE_BUBBLE))
                handler.post(FloatingOverlayService.this::hideBubble);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "FloatingOverlayService STARTED1");

        startForegroundWithNotification();   // Keep alive on Vivo/Xiaomi/Oppo

        apiService    = AIWritingApp.getInstance().getApiService();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        inflateBubble();
        Log.e(TAG, "FloatingOverlayService STARTED2");
        inflatePanel();
        Log.e(TAG, "FloatingOverlayService STARTED3");
        registerBroadcastReceiver();
        Log.e(TAG, "FloatingOverlayService STARTED4 — fully ready");
    }

    // ── Foreground notification — prevents OEM from killing service ─
    private void startForegroundWithNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "AI Writing Overlay",
                NotificationManager.IMPORTANCE_MIN); // silent, no sound
        channel.setShowBadge(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AI Writing Assistant")
                .setContentText("Ready to help — tap ⚡ in any app")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }


    }

    // ── Bubble: ⚡ icon, draggable, above keyboard ────────────────
    private void inflateBubble() {
        bubbleView = LayoutInflater.from(this)
                .inflate(R.layout.overlay_bubble_only, null);

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.BOTTOM | Gravity.END;
        bubbleParams.x = 16;
        bubbleParams.y = 260; // above soft keyboard

        bubbleView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX      = bubbleParams.x;
                    initialY      = bubbleParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    bubbleParams.x = initialX + (int)(initialTouchX - event.getRawX());
                    bubbleParams.y = initialY + (int)(event.getRawY() - initialTouchY);
                    if (bubbleAdded) windowManager.updateViewLayout(bubbleView, bubbleParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getRawX() - initialTouchX);
                    float dy = Math.abs(event.getRawY() - initialTouchY);
                    if (dx < 8 && dy < 8) {
                        Log.e(TAG, "BUBBLE TAPPED — isPanelOpen=" + isPanelOpen);
                        if (isPanelOpen) closePanel(); else openPanel();
                    }
                    return true;
            }
            return false;
        });
    }

    // ── Panel: full-width, above keyboard ────────────────────────
    private void inflatePanel() {
        panelView = LayoutInflater.from(this)
                .inflate(R.layout.overlay_action_panel, null);

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.y = 300;

        wireActionButtons();
    }

    private void wireActionButtons() {
        TextView tvResult = panelView.findViewById(R.id.tv_result);

        panelView.findViewById(R.id.btn_improve) .setOnClickListener(v -> callAI("rewrite", "professional", tvResult));
        panelView.findViewById(R.id.btn_shorten) .setOnClickListener(v -> callAI("shorten", "short",        tvResult));
        panelView.findViewById(R.id.btn_formal)  .setOnClickListener(v -> callAI("rewrite", "formal",       tvResult));
        panelView.findViewById(R.id.btn_grammar) .setOnClickListener(v -> callAI("grammar", "professional", tvResult));
        panelView.findViewById(R.id.btn_reply)   .setOnClickListener(v -> callAI("reply",   "friendly",     tvResult));
        panelView.findViewById(R.id.btn_expand)  .setOnClickListener(v -> callAI("expand",  "professional", tvResult));
        panelView.findViewById(R.id.btn_close_panel).setOnClickListener(v -> closePanel());

        // Tap result → paste directly into focused field
        tvResult.setOnClickListener(v -> {
            String raw = tvResult.getText().toString();
            if (raw.isEmpty() || raw.equals("Thinking…")
                    || raw.startsWith("Error") || raw.startsWith("Network")
                    || raw.startsWith("No text")) return;

            String clean = raw.replace("\n\n(tap to paste)", "").trim();

            // 1. Direct accessibility paste
            ContextReaderService.pasteIntoFocusedField(clean);

            // 2. Clipboard fallback
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("AI", clean));

            Toast.makeText(this, "✓ Pasted!", Toast.LENGTH_SHORT).show();
            closePanel();
        });
    }

    // ── Show bubble ───────────────────────────────────────────────
    private void showBubble() {
        Log.e(TAG, "SHOW BUBBLE CALLED, bubbleAdded=" + bubbleAdded);
        try {
            if (!bubbleAdded) {
                windowManager.addView(bubbleView, bubbleParams);
                bubbleAdded = true;
                Log.e(TAG, "BUBBLE ADDED TO WINDOW MANAGER");
            }
            bubbleView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "showBubble ERROR: " + e.getMessage());
        }
    }

    // ── Hide bubble + close panel ─────────────────────────────────
    private void hideBubble() {
        Log.e(TAG, "HIDE BUBBLE CALLED");
        closePanel();
        if (bubbleAdded) bubbleView.setVisibility(View.GONE);
    }

    // ── Open panel ────────────────────────────────────────────────
    private void openPanel() {
        ContextReaderService.isPanelOpen = true;
        Log.e(TAG, "OPEN PANEL CALLED");
        try {
            if (!panelAdded) {
                windowManager.addView(panelView, panelParams);
                panelAdded = true;
                Log.e(TAG, "PANEL ADDED TO WINDOW MANAGER");
            }
            panelView.setVisibility(View.VISIBLE);
            isPanelOpen = true;
        } catch (Exception e) {
            Log.e(TAG, "openPanel ERROR: " + e.getMessage());
        }
    }

    // ── Close panel ───────────────────────────────────────────────
    private void closePanel() {
        ContextReaderService.isPanelOpen = false;
        if (panelAdded) panelView.setVisibility(View.GONE);
        isPanelOpen = false;
        // Reset result view
        TextView tvResult = panelView.findViewById(R.id.tv_result);
        if (tvResult != null) {
            tvResult.setText("");
            tvResult.setVisibility(View.GONE);
        }
    }

    // ── AI call ───────────────────────────────────────────────────
    private void callAI(String action, String tone, TextView resultView) {
        // Get best available context
        String text = ContextReaderService.getLastScreenContext();
        if (text == null || text.trim().isEmpty())
            text = NotificationContextStore.getInstance().getLastNotificationText();

        if (text == null || text.trim().isEmpty()) {
            resultView.setVisibility(View.VISIBLE);
            resultView.setText("No text found.\nType something or copy text first.");
            return;
        }

        Log.e(TAG, "TEXT SENT TO API: [" + text + "]");
        resultView.setVisibility(View.VISIBLE);
        resultView.setText("Thinking…");

        RewriteRequest req = new RewriteRequest();
        req.setText(text);
        req.setAction(action);
        req.setTone(tone);
        req.setAppContext(ContextReaderService.getLastPackageName());

        apiService.rewrite(req).enqueue(new Callback<RewriteResponse>() {
            @Override
            public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> resp) {
                handler.post(() -> {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String result = resp.body().getRewritten();
                        resultView.setText(result + "\n\n(tap to paste)");
                    } else {
                        resultView.setText("Error " + resp.code() + " — try again");
                    }
                });
            }
            @Override
            public void onFailure(Call<RewriteResponse> call, Throwable t) {
                handler.post(() ->
                        resultView.setText("Network error — check connection"));
            }
        });
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ContextReaderService.ACTION_SHOW_BUBBLE);
        filter.addAction(ContextReaderService.ACTION_HIDE_BUBBLE);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    // START_STICKY — auto-restart if killed by system
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        try { if (bubbleAdded && bubbleView != null) windowManager.removeView(bubbleView); }
        catch (Exception ignored) {}
        try { if (panelAdded && panelView != null) windowManager.removeView(panelView); }
        catch (Exception ignored) {}
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}