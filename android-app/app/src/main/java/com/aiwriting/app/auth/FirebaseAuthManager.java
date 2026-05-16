package com.aiwriting.app.auth;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class FirebaseAuthManager {

    private static final String TAG = "FirebaseAuthManager";

    private final FirebaseAuth firebaseAuth;
    private final Context context;
    private final String webClientId;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user, String idToken);
        void onFailure(String error);
    }

    public interface TokenCallback {
        void onResult(String token);
    }

    /** Safe to call from Application — no Activity needed */
    public FirebaseAuthManager(Context context, String webClientId) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.context = context.getApplicationContext();
        this.webClientId = webClientId;
    }

    /** GoogleSignInClient requires a Context — safe with Application context */
    private GoogleSignInClient getGoogleClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    public Intent getGoogleSignInIntent() {
        return getGoogleClient().getSignInIntent();
    }

    public void handleGoogleSignInResult(Task<GoogleSignInAccount> task, AuthCallback callback) {
        try {
            GoogleSignInAccount account = task.getResult();
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user != null) {
                            user.getIdToken(true)
                                    .addOnSuccessListener(r -> callback.onSuccess(user, r.getToken()))
                                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        } else {
                            callback.onFailure("User null after sign-in");
                        }
                    })
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } catch (Exception e) {
            callback.onFailure(e.getMessage());
        }
    }

    public void getFreshToken(TokenCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) { callback.onResult(null); return; }
        user.getIdToken(false)
                .addOnSuccessListener(r -> callback.onResult(r.getToken()))
                .addOnFailureListener(e -> { Log.e(TAG, "Token refresh failed", e); callback.onResult(null); });
    }

    public FirebaseUser getCurrentUser() { return firebaseAuth.getCurrentUser(); }
    public boolean isLoggedIn() { return firebaseAuth.getCurrentUser() != null; }

    public void signOut() {
        firebaseAuth.signOut();
        getGoogleClient().signOut();
    }
}