package com.aiwriting.app.keyboard;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aiwriting.app.AIWritingApp;
import com.aiwriting.app.R;
import com.aiwriting.app.api.BackendApiService;
import com.aiwriting.app.model.RewriteRequest;
import com.aiwriting.app.model.RewriteResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AIKeyboardService extends InputMethodService {

    private static final String TAG = "AIKeyboard";
    private static final long DEBOUNCE_MS = 1200;

    private static final String[] TONES  = {"professional","friendly","formal","short","gen_z","apology","assertive","empathetic","flirty","casual"};
    private static final String[] TONE_L = {"Pro 💼","Friendly 😊","Formal 🎩","Short ✂️","Gen Z 🔥","Sorry 🙏","Bold 💪","Warm 🤗","Flirty 😉","Casual 😎"};

    private static final String[] ACTIONS  = {"rewrite","grammar","shorten","expand","reply","email","bullet","summarize","casual","subject"};
    private static final String[] ACTION_L = {"Rewrite ✨","Grammar ✅","Shorten ✂️","Expand 📝","Reply 💬","Email 📧","Bullets 🔹","Summarize 📋","Make Casual 😎","Subject Line 📌"};

    private View candidatesView;
    private View scrollResults;          // ← NEW reference
    private LinearLayout llTones, llActions, llResults;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String selectedTone   = "professional";
    private String selectedAction = "rewrite";
    private String currentText    = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private BackendApiService apiService;

    private TextView lastToneBtn, lastActionBtn;

    @Override
    public void onCreate() {
        super.onCreate();
        apiService = AIWritingApp.getInstance().getApiService();
    }

    @Override
    public View onCreateCandidatesView() {
        candidatesView = LayoutInflater.from(this)
                .inflate(R.layout.keyboard_suggestion_bar, null);

        llTones      = candidatesView.findViewById(R.id.ll_tones);
        llActions    = candidatesView.findViewById(R.id.ll_actions);
        llResults    = candidatesView.findViewById(R.id.ll_results);
        scrollResults = candidatesView.findViewById(R.id.scroll_results); // ← grab parent
        progressBar  = candidatesView.findViewById(R.id.loading_indicator);
        tvStatus     = candidatesView.findViewById(R.id.tv_status);

        buildToneButtons();
        buildActionButtons();

        candidatesView.findViewById(R.id.btn_fix_it).setOnClickListener(v -> {
            fetchCurrentText();
            if (!currentText.trim().isEmpty()) callGroq(currentText);
        });

        return candidatesView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        setCandidatesViewShown(true);
        clearResults();
        fetchCurrentText();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        cancelDebounce();
        clearResults();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd,
                newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        fetchCurrentText();
        scheduleDebounce();
    }

    private void fetchCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(500, 0);
        if (before != null) currentText = before.toString();
    }

    private void scheduleDebounce() {
        cancelDebounce();
        if (currentText.trim().length() < 8) { clearResults(); return; }
        debounceRunnable = () -> callGroq(currentText);
        handler.postDelayed(debounceRunnable, DEBOUNCE_MS);
    }

    private void cancelDebounce() {
        if (debounceRunnable != null) handler.removeCallbacks(debounceRunnable);
    }

    private void buildToneButtons() {
        llTones.removeAllViews();
        for (int i = 0; i < TONES.length; i++) {
            TextView btn = makePill(TONE_L[i]);
            final int idx = i;
            btn.setOnClickListener(v -> {
                selectedTone = TONES[idx];
                highlightTone(btn);
                if (!currentText.trim().isEmpty()) callGroq(currentText);
            });
            if (i == 0) { highlightTone(btn); lastToneBtn = btn; }
            llTones.addView(btn);
        }
    }

    private void buildActionButtons() {
        llActions.removeAllViews();
        for (int i = 0; i < ACTIONS.length; i++) {
            TextView btn = makePill(ACTION_L[i]);
            final int idx = i;
            btn.setOnClickListener(v -> {
                selectedAction = ACTIONS[idx];
                highlightAction(btn);
                if (!currentText.trim().isEmpty()) callGroq(currentText);
            });
            if (i == 0) { highlightAction(btn); lastActionBtn = btn; }
            llActions.addView(btn);
        }
    }

    private TextView makePill(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setPadding(24, 10, 24, 10);
        tv.setClickable(true);
        tv.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 4, 6, 4);
        tv.setLayoutParams(lp);
        tv.setBackgroundResource(R.drawable.bg_chip_unselected);
        tv.setTextColor(getColor(R.color.text_primary));
        return tv;
    }

    private void highlightTone(TextView btn) {
        if (lastToneBtn != null) {
            lastToneBtn.setBackgroundResource(R.drawable.bg_chip_unselected);
            lastToneBtn.setTextColor(getColor(R.color.text_primary));
        }
        btn.setBackgroundResource(R.drawable.bg_chip_selected);
        btn.setTextColor(getColor(android.R.color.white));
        lastToneBtn = btn;
    }

    private void highlightAction(TextView btn) {
        if (lastActionBtn != null) {
            lastActionBtn.setBackgroundResource(R.drawable.bg_chip_unselected);
            lastActionBtn.setTextColor(getColor(R.color.text_primary));
        }
        btn.setBackgroundResource(R.drawable.bg_chip_selected);
        btn.setTextColor(getColor(android.R.color.white));
        lastActionBtn = btn;
    }

    private void callGroq(String text) {
        if (progressBar == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("AI thinking…");
        clearResults();

        String appContext = NotificationContextStore.getInstance().getCurrentApp();

        RewriteRequest req = new RewriteRequest();
        req.setText(text);
        req.setTone(selectedTone);
        req.setAction(selectedAction);
        req.setAppContext(appContext);

        apiService.rewrite(req).enqueue(new Callback<RewriteResponse>() {
            @Override
            public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> resp) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setVisibility(View.GONE);
                    if (resp.isSuccessful() && resp.body() != null) {
                        showResult(resp.body().getRewritten());
                        if (resp.body().getSuggestions() != null) {
                            for (String s : resp.body().getSuggestions()) showResult(s);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<RewriteResponse> call, Throwable t) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Tap 'Fix It' to retry");
                    tvStatus.setVisibility(View.VISIBLE);
                    Log.e(TAG, "API fail: " + t.getMessage());
                });
            }
        });
    }

    private void showResult(String text) {
        if (text == null || text.trim().isEmpty()) return;
        TextView tv = new TextView(this);
        tv.setText("✦ " + text);
        tv.setTextSize(13f);
        tv.setTextColor(getColor(R.color.primary));
        tv.setPadding(20, 12, 20, 12);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setBackgroundResource(R.drawable.bg_result_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 0, 6, 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> insertText(text));

        llResults.addView(tv);
        llResults.setVisibility(View.VISIBLE);

        // ── FIX: also show the parent HorizontalScrollView ──────
        if (scrollResults != null) scrollResults.setVisibility(View.VISIBLE);
    }

    private void insertText(String newText) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(currentText.length(), 0);
        ic.commitText(newText, 1);
        ic.endBatchEdit();
        currentText = newText;
        clearResults();
    }

    private void clearResults() {
        if (llResults != null)    { llResults.removeAllViews(); llResults.setVisibility(View.GONE); }
        if (scrollResults != null) scrollResults.setVisibility(View.GONE); // ← hide parent too
        if (tvStatus != null)     tvStatus.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        cancelDebounce();
        super.onDestroy();
    }
}