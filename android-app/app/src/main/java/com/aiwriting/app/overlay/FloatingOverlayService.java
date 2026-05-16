package com.aiwriting.app.overlay;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aiwriting.app.AIWritingApp;
import com.aiwriting.app.R;
import com.aiwriting.app.accessibility.ContextReaderService;
import com.aiwriting.app.api.BackendApiService;
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

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isPanelOpen = false;

    @Override
    public void onCreate() {
        super.onCreate();
        apiService = AIWritingApp.getInstance().getApiService();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupOverlay();
    }

    private void setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null);
        actionPanel = overlayView.findViewById(R.id.action_panel);
        bubbleView  = overlayView.findViewById(R.id.iv_bubble);
        actionPanel.setVisibility(View.GONE);

        // KEY FIX: Use FLAG_NOT_FOCUSABLE only for bubble, switch flags when panel opens
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initialX + (int)(initialTouchX - event.getRawX());
                    params.y = initialY + (int)(event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(overlayView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                            Math.abs(event.getRawY() - initialTouchY) < 10) {
                        togglePanel();
                    }
                    return true;
            }
            return false;
        });
    }

    private void togglePanel() {
        isPanelOpen = !isPanelOpen;
        actionPanel.setVisibility(isPanelOpen ? View.VISIBLE : View.GONE);

        // KEY FIX: Remove NOT_FOCUSABLE when panel is open so buttons are clickable
        if (isPanelOpen) {
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        windowManager.updateViewLayout(overlayView, params);
    }

    private void setupActionButtons() {
        TextView tvResult = overlayView.findViewById(R.id.tv_result);

        overlayView.findViewById(R.id.btn_improve).setOnClickListener(v -> callAI("rewrite", "professional", tvResult));
        overlayView.findViewById(R.id.btn_shorten).setOnClickListener(v -> callAI("shorten", "short", tvResult));
        overlayView.findViewById(R.id.btn_formal).setOnClickListener(v -> callAI("rewrite", "formal", tvResult));
        overlayView.findViewById(R.id.btn_grammar).setOnClickListener(v -> callAI("grammar", "professional", tvResult));
        overlayView.findViewById(R.id.btn_reply).setOnClickListener(v -> callAI("reply", "friendly", tvResult));
        overlayView.findViewById(R.id.btn_expand).setOnClickListener(v -> callAI("expand", "professional", tvResult));

        // Copy result on click
        tvResult.setOnClickListener(v -> {
            String text = tvResult.getText().toString();
            if (!text.isEmpty() && !text.equals("Thinking…") && !text.startsWith("No text")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("AI", text));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
        });

        overlayView.findViewById(R.id.btn_close_panel).setOnClickListener(v -> togglePanel());
    }

    private void callAI(String action, String tone, TextView resultView) {
        // Priority: clipboard → screen context → notification context
        String text = getClipboardText();
        if (text == null || text.trim().isEmpty()) text = ContextReaderService.getLastScreenContext();
        if (text == null || text.trim().isEmpty()) text = com.aiwriting.app.keyboard.NotificationContextStore.getInstance().getLastNotificationText();

        if (text == null || text.trim().isEmpty()) {
            resultView.setVisibility(View.VISIBLE);
            resultView.setText("Copy some text or open a chat first.");
            return;
        }

        resultView.setVisibility(View.VISIBLE);
        resultView.setText("Thinking…");

        String appContext = ContextReaderService.getLastPackageName();
        final String finalText = text;

        RewriteRequest request = new RewriteRequest();
        request.setText(finalText);
        request.setAction(action);
        request.setTone(tone);
        request.setAppContext(appContext);

        apiService.rewrite(request).enqueue(new Callback<RewriteResponse>() {
            @Override
            public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().getRewritten();
                    resultView.setText(result + "\n\n(tap to copy)");
                } else {
                    resultView.setText("Error " + response.code());
                }
            }
            @Override
            public void onFailure(Call<RewriteResponse> call, Throwable t) {
                resultView.setText("Failed: " + t.getMessage());
            }
        });
    }

    private String getClipboardText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                return text != null ? text.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void onDestroy() {
        if (overlayView != null) windowManager.removeView(overlayView);
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
