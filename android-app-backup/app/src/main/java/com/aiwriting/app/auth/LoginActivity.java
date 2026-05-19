package com.aiwriting.app.auth;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.aiwriting.app.R;
import com.aiwriting.app.ui.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 1001;

    private FirebaseAuthManager authManager;
    private ProgressBar progressBar;
    private Button btnGoogleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new FirebaseAuthManager(this, getString(R.string.default_web_client_id));

        // If already logged in, skip to main
        if (authManager.isLoggedIn()) {
            goToMain();
            return;
        }

        progressBar = findViewById(R.id.progress_bar);
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);

        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
    }

    private void startGoogleSignIn() {
        showLoading(true);
        Intent signInIntent = authManager.getGoogleSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            authManager.handleGoogleSignInResult(task, new FirebaseAuthManager.AuthCallback() {
                @Override
                public void onSuccess(com.google.firebase.auth.FirebaseUser user, String idToken) {
                    Log.i(TAG, "Signed in as: " + user.getEmail());
                    showLoading(false);
                    // Backend will auto-create profile on first /api/user/profile call
                    goToMain();
                }

                @Override
                public void onFailure(String error) {
                    showLoading(false);
                    Toast.makeText(LoginActivity.this,
                            "Sign-in failed: " + error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Sign-in failure: " + error);
                }
            });
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnGoogleSignIn.setEnabled(!show);
    }
}