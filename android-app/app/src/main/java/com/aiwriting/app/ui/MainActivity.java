package com.aiwriting.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.aiwriting.app.R;
import com.aiwriting.app.auth.FirebaseAuthManager;
import com.aiwriting.app.auth.LoginActivity;
import com.aiwriting.app.overlay.FloatingOverlayService;
import com.aiwriting.app.util.PermissionHelper;
import com.aiwriting.app.util.SessionManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RewriteViewModel viewModel;
    private FirebaseAuthManager authManager;
    private SessionManager sessionManager;

    private EditText etInput;
    private TextView tvOutput;
    private Button btnRewrite;
    private ProgressBar progressBar;
    private ChipGroup chipGroupTone;
    private ChipGroup chipGroupAction;
    private TextView tvUsage, tvUserName;
    private View cardResult;
    private View setupBannerKeyboard, setupBannerOverlay, setupBannerAccessibility;

    // Multi-select state
    private final List<String> selectedTones   = new ArrayList<>();
    private final List<String> selectedActions = new ArrayList<>();

    // ── Tones: 10 options ──────────────────────────────────────
    private static final String[] TONES  = {
            "professional","friendly","formal","short","confident",
            "gen_z","apology","flirty","empathetic","assertive"
    };
    private static final String[] TONE_LABELS = {
            "Professional 💼","Friendly 😊","Formal 🎩","Short ✂️","Confident 💪",
            "Gen Z 🔥","Apology 🙏","Flirty 😉","Empathetic 🤝","Assertive ⚡"
    };

    // ── Actions: 10 options ────────────────────────────────────
    private static final String[] ACTIONS = {
            "rewrite","grammar","shorten","expand","email",
            "reply","bullet","casual","summarize","subject"
    };
    private static final String[] ACTION_LABELS = {
            "Rewrite ✨","Fix Grammar ✅","Shorten ✂️","Expand 📝","Write Email 📧",
            "Smart Reply 💬","Bullet Points 📌","Make Casual 😎","Summarize 📋","Email Subject 📌"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = new FirebaseAuthManager(getApplicationContext(), getString(R.string.default_web_client_id));
        if (!authManager.isLoggedIn()) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }

        sessionManager = SessionManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(RewriteViewModel.class);

        bindViews();
        observeViewModel();
        setupToneChips();
        setupActionChips();
        setupPermissionBanners();
        setupButtons();
        viewModel.loadProfile();
        viewModel.loadUsage();
    }

    private void bindViews() {
        etInput                  = findViewById(R.id.et_input);
        tvOutput                 = findViewById(R.id.tv_output);
        btnRewrite               = findViewById(R.id.btn_rewrite);
        progressBar              = findViewById(R.id.progress_bar);
        chipGroupTone            = findViewById(R.id.chip_group_tone);
        chipGroupAction          = findViewById(R.id.chip_group_action);
        tvUsage                  = findViewById(R.id.tv_usage);
        tvUserName               = findViewById(R.id.tv_user_name);
        cardResult               = findViewById(R.id.card_result);
        setupBannerKeyboard      = findViewById(R.id.banner_keyboard);
        setupBannerOverlay       = findViewById(R.id.banner_overlay);
        setupBannerAccessibility = findViewById(R.id.banner_accessibility);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnRewrite.setEnabled(!loading);
        });

        viewModel.getRewriteResult().observe(this, response -> {
            if (response != null) {
                cardResult.setVisibility(View.VISIBLE);
                tvOutput.setVisibility(View.VISIBLE);
                tvOutput.setText(response.getRewritten());
                if (response.getSuggestions() != null && !response.getSuggestions().isEmpty())
                    showSuggestions(response.getSuggestions());
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });

        viewModel.getUserProfile().observe(this, profile -> {
            if (profile != null) {
                String first = profile.getDisplayName() != null ? profile.getDisplayName().split(" ")[0] : "there";
                tvUserName.setText("Hi, " + first + " 👋");
                sessionManager.saveUser(profile.getUid(), profile.getEmail(), profile.getDisplayName(), profile.isPro());
            }
        });

        viewModel.getUsageStats().observe(this, stats -> {
            if (stats != null)
                tvUsage.setText(stats.getDailyUsage() + "/" + stats.getDailyLimit() + " today" + (stats.isPro() ? " ✨" : ""));
        });
    }

    private void setupToneChips() {
        // Default: professional selected
        selectedTones.add("professional");
        for (int i = 0; i < TONES.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(TONE_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            // Multi-select: no singleSelection on ChipGroup
            final String tone = TONES[i];
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) { if (!selectedTones.contains(tone)) selectedTones.add(tone); }
                else selectedTones.remove(tone);
                if (selectedTones.isEmpty()) { selectedTones.add(tone); chip.setChecked(true); }
            });
            chipGroupTone.addView(chip);
        }
    }

    private void setupActionChips() {
        // Default: rewrite selected
        selectedActions.add("rewrite");
        for (int i = 0; i < ACTIONS.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(ACTION_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            final String action = ACTIONS[i];
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) { if (!selectedActions.contains(action)) selectedActions.add(action); }
                else selectedActions.remove(action);
                if (selectedActions.isEmpty()) { selectedActions.add(action); chip.setChecked(true); }
            });
            chipGroupAction.addView(chip);
        }
    }

    private void setupPermissionBanners() {
        setupBannerKeyboard.setVisibility(PermissionHelper.isKeyboardEnabled(this) ? View.GONE : View.VISIBLE);
        setupBannerKeyboard.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        setupBannerOverlay.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        setupBannerOverlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));

        setupBannerAccessibility.setVisibility(PermissionHelper.isAccessibilityEnabled(this) ? View.GONE : View.VISIBLE);
        setupBannerAccessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void setupButtons() {
        btnRewrite.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) { Toast.makeText(this, "Enter some text first", Toast.LENGTH_SHORT).show(); return; }

            // Use first selected action + combine tones into instruction
            String action = selectedActions.isEmpty() ? "rewrite" : selectedActions.get(0);
            String tone   = selectedTones.isEmpty()   ? "professional" : String.join(",", selectedTones);
            viewModel.rewrite(input, tone, action, "app");
        });

        tvOutput.setOnLongClickListener(v -> {
            String text = tvOutput.getText().toString();
            if (!text.isEmpty()) {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(android.content.ClipData.newPlainText("result", text));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.btn_sign_out).setOnClickListener(v -> {
            authManager.signOut(); sessionManager.clear();
            startActivity(new Intent(this, LoginActivity.class)); finish();
        });

        Button btnOverlay = findViewById(R.id.btn_toggle_overlay);
        btnOverlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                return;
            }
            if (sessionManager.isOverlayEnabled()) {
                stopService(new Intent(this, FloatingOverlayService.class));
                sessionManager.setOverlayEnabled(false);
                btnOverlay.setText("Enable Floating Bubble");
            } else {
                startService(new Intent(this, FloatingOverlayService.class));
                sessionManager.setOverlayEnabled(true);
                btnOverlay.setText("Disable Floating Bubble");
            }
        });
    }

    private void showSuggestions(List<String> suggestions) {
        TextView s1 = findViewById(R.id.tv_suggestion_1);
        TextView s2 = findViewById(R.id.tv_suggestion_2);
        if (suggestions.size() > 0) { s1.setText(suggestions.get(0)); s1.setVisibility(View.VISIBLE); s1.setOnClickListener(v -> etInput.setText(suggestions.get(0))); }
        if (suggestions.size() > 1) { s2.setText(suggestions.get(1)); s2.setVisibility(View.VISIBLE); s2.setOnClickListener(v -> etInput.setText(suggestions.get(1))); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupPermissionBanners();
        viewModel.loadUsage();
    }
}
