package com.aiwriting.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aiwriting.app.AIWritingApp;
import com.aiwriting.app.api.BackendApiService;
import com.aiwriting.app.model.RewriteRequest;
import com.aiwriting.app.model.RewriteResponse;
import com.aiwriting.app.model.UsageStats;
import com.aiwriting.app.model.UserProfile;

import retrofit2.Call;
import android.util.Log;
import retrofit2.Callback;
import retrofit2.Response;

public class RewriteViewModel extends AndroidViewModel {

    private final BackendApiService apiService;

    private final MutableLiveData<RewriteResponse> rewriteResult = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<UserProfile> userProfile = new MutableLiveData<>();
    private final MutableLiveData<UsageStats> usageStats = new MutableLiveData<>();

    public RewriteViewModel(@NonNull Application application) {
        super(application);
        apiService = AIWritingApp.getInstance().getApiService();
    }

    // ── Observables ──────────────────────────────────────────────

    public LiveData<RewriteResponse> getRewriteResult() { return rewriteResult; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<UserProfile> getUserProfile() { return userProfile; }
    public LiveData<UsageStats> getUsageStats() { return usageStats; }

    // ── Actions ──────────────────────────────────────────────────

    public void rewrite(String text, String tone, String action, String appContext) {
        isLoading.setValue(true);

        RewriteRequest request = new RewriteRequest();
        request.setText(text);
        request.setTone(tone);
        request.setAction(action);
        request.setAppContext(appContext);

        apiService.rewrite(request).enqueue(new Callback<RewriteResponse>() {
            @Override
            public void onResponse(Call<RewriteResponse> call, Response<RewriteResponse> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    rewriteResult.postValue(response.body());
                } else {
                    errorMessage.postValue("Request failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<RewriteResponse> call, Throwable t) {
                isLoading.postValue(false);
                errorMessage.postValue("Network error: " + t.getMessage());
            }
        });
    }

    public void loadProfile() {
        apiService.getProfile().enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                if (response.isSuccessful() && response.body() != null) {
                    userProfile.postValue(response.body());
                }
            }

            @Override
            public void onFailure(Call<UserProfile> call, Throwable t) {
                Log.w("ViewModel", "Profile load failed: " + t.getMessage());
            }
        });
    }

    public void loadUsage() {
        apiService.getUsage().enqueue(new Callback<UsageStats>() {
            @Override
            public void onResponse(Call<UsageStats> call, Response<UsageStats> response) {
                if (response.isSuccessful() && response.body() != null) {
                    usageStats.postValue(response.body());
                }
            }

            @Override
            public void onFailure(Call<UsageStats> call, Throwable t) {
                Log.w("ViewModel", "Usage load failed: " + t.getMessage());
            }
        });
    }
}
