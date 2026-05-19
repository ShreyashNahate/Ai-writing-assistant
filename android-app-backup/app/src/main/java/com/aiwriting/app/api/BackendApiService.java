package com.aiwriting.app.api;

import com.aiwriting.app.model.RewriteRequest;
import com.aiwriting.app.model.RewriteResponse;
import com.aiwriting.app.model.UserProfile;
import com.aiwriting.app.model.UsageStats;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface BackendApiService {

    @POST("api/rewrite")
    Call<RewriteResponse> rewrite(@Body RewriteRequest request);

    @GET("api/user/profile")
    Call<UserProfile> getProfile();

    @GET("api/user/usage")
    Call<UsageStats> getUsage();

    @PUT("api/user/tone")
    Call<Map<String, String>> updateTone(@Body Map<String, String> body);

    @GET("api/health")
    Call<Map<String, String>> health();
}