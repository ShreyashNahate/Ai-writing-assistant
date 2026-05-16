package com.aiwriting.app.api;

import android.content.Context;
import android.util.Log;

import com.aiwriting.app.BuildConfig;
import com.aiwriting.app.auth.FirebaseAuthManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class BackendApiClient {

    private static final String TAG = "BackendApiClient";
    private static BackendApiClient instance;
    private final BackendApiService apiService;

    private BackendApiClient(Context context) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    // OkHttp interceptors run on a background thread — Tasks.await() is safe here
                    String token = null;
                    try {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            token = Tasks.await(user.getIdToken(false)).getToken();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Token fetch failed: " + e.getMessage());
                    }

                    Request.Builder builder = chain.request().newBuilder()
                            .header("Content-Type", "application/json");
                    if (token != null) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .build();

        apiService = new Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build()
                .create(BackendApiService.class);
    }

    public static synchronized BackendApiClient getInstance(Context context, FirebaseAuthManager unused) {
        if (instance == null) {
            instance = new BackendApiClient(context.getApplicationContext());
        }
        return instance;
    }

    public BackendApiService getService() { return apiService; }
}
