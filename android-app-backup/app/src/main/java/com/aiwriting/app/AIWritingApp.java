package com.aiwriting.app;

import android.app.Application;

import com.aiwriting.app.api.BackendApiClient;
import com.aiwriting.app.api.BackendApiService;
import com.aiwriting.app.auth.FirebaseAuthManager;
import com.google.firebase.FirebaseApp;

public class AIWritingApp extends Application {

    private static AIWritingApp instance;
    private FirebaseAuthManager authManager;
    private BackendApiService apiService;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        FirebaseApp.initializeApp(this);

        // Pass 'this' (Application context) — no Activity needed
        authManager = new FirebaseAuthManager(this, getString(R.string.default_web_client_id));
        apiService  = BackendApiClient.getInstance(this, authManager).getService();
    }

    public static AIWritingApp getInstance() { return instance; }
    public FirebaseAuthManager getAuthManager() { return authManager; }
    public BackendApiService getApiService() { return apiService; }
}