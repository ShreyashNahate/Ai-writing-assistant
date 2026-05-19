package com.aiwriting.app.overlay;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

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

    private WindowManager windowManager;
    private View overlayView;
    private View actionPanel;
    private View bubbleView;
    private BackendApiService apiService;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isPanelOpen = false;

    @Override
    public void onCreate() {
        super.onCreate();
        apiService    = AIWritingApp.getInstance().getApiService();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupOverlay();
    }

    private void setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null);
        actionPanel = overlayView.findViewById(R.id.action_panel);
        bubbleView  = overlayView.findViewById(R.id.floatingBubble);
        actionPanel.setVisibility(View.GONE);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 300;

        windowManager.addView(overlayView, params);
        setupDragging();
        setupActionButtons();
    }

    private void setupDragging() {
        bubbleView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x; initialY = params.y;
                    initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int)(initialTouchX - event.getRawX());
                    params.y = initialY + (int)(event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(overlayView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                            Math.abs(event.getRawY() - initialTouchY) < 10) togglePanel();
                    return true;
            }
            return false;
        });
    }

    private void togglePanel() {
        isPanelOpen = !isPanelOpen;
        actionPanel.setVisibility(isPanelOpen ? View.VISIBLE : View.GONE);
        // Remove NOT_FOCUSABLE when panel is open so buttons are tappable
        params.flags = isPanelOpen
                ? WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        windowManager.updateViewLayout(overlayView, params);
    }

    private void setupActionButtons() {
        TextView tvResult = overlayView.findViewById(R.id.tv_result);

        overlayView.findViewById(R.id.btn_improve).setOnClickListener(v -> callAI("rewrite",  "professional", tvResult));
        overlayView.findViewById(R.id.btn_shorten).setOnClickListener(v -> callAI("shorten",  "short",        tvResult));
        overlayView.findViewById(R.id.btn_formal) .setOnClickListener(v -> callAI("rewrite",  "formal",       tvResult));
        overlayView.findViewById(R.id.btn_grammar).setOnClickListener(v -> callAI("grammar",  "professional", tvResult));
        overlayView.findViewById(R.id.btn_reply)  .setOnClickListener(v -> callAI("reply",    "friendly",     tvResult));
        overlayView.findViewById(R.id.btn_expand) .setOnClickListener(v -> callAI("expand",   "professional", tvResult));
        overlayView.findViewById(R.id.btn_close_panel).setOnClickListener(v -> togglePanel());

        // Tap result → paste into active text field
        tvResult.setOnClickListener(v -> {
            String raw = tvResult.getText().toString();
            if (raw.isEmpty() || raw.equals("Thinking…") ||
                    raw.startsWith("No text") || raw.startsWith("Error") ||
                    raw.startsWith("Failed")) return;

            String clean = raw.replace("\n\n(tap to paste)", "").trim();
            ContextReaderService.pasteIntoFocusedField(clean);
            Toast.makeText(this, "✓ Pasted!", Toast.LENGTH_SHORT).show();
            togglePanel();
        });
    }

    private void callAI(String action, String tone, TextView resultView) {
        String text;
        String appContext = ContextReaderService.getLastPackageName();

        if ("reply".equals(action)) {
            // For Reply: use last 2 messages from the conversation (what opponent wrote)
            // Priority: screen conversation → last notification
            text = ContextReaderService.getLastConversationContext();
            if (text == null || text.trim().isEmpty()) {
                text = NotificationContextStore.getInstance().getLastNotificationText();
            }
            if (text == null || text.trim().isEmpty()) {
                resultView.setVisibility(View.VISIBLE);
                resultView.setText("No conversation found.\nOpen a chat and try again.");
                return;
            }
        } else {
            // For all other actions: use what the user is currently typing in the text field
            text = ContextReaderService.getCurrentEditableText();
            if (text == null || text.trim().isEmpty()) {
                resultView.setVisibility(View.VISIBLE);
                resultView.setText("No text found in the input field.\nType something first.");
                return;
            }
        }

        resultView.setVisibility(View.VISIBLE);
        resultView.setText("Thinking…");

        final String finalText = text;
        RewriteRequest req = new RewriteRequest();
        req.setText(finalText);
        req.setAction(action);
        req.setTone(tone);
        req.setAppContext(appContext);

        apiService.rewrite(req).enqueue(new Callback<RewriteResponse>() {
            @Override
            public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> resp) {
                handler.post(() -> {
                    if (resp.isSuccessful() && resp.body() != null) {
                        resultView.setText(resp.body().getRewritten() + "\n\n(tap to paste)");
                    } else {
                        resultView.setText("Error " + resp.code() + " — try again");
                    }
                });
            }
            @Override
            public void onFailure(Call<RewriteResponse> call, Throwable t) {
                handler.post(() -> resultView.setText("Failed: check connection"));
            }
        });
    }

    @Override
    public void onDestroy() {
        try { if (overlayView != null) windowManager.removeView(overlayView); }
        catch (Exception ignored) {}
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
