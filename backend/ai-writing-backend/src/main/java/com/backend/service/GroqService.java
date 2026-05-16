package com.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GroqService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.base-url}")
    private String baseUrl;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.model-fast}")
    private String modelFast;

    @Value("${groq.api.model-smart}")
    private String modelSmart;

    @Value("${groq.api.max-tokens}")
    private int maxTokens;

    @Value("${groq.api.timeout-seconds}")
    private int timeoutSeconds;

    public GroqService() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Call Groq API with a prompt. Uses fast model by default.
     * Switch to smart model for email generation.
     */
    public String complete(String systemPrompt, String userPrompt, boolean useSmartModel) throws IOException {
        String model = useSmartModel ? modelSmart : modelFast;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.7);

        ArrayNode messages = body.putArray("messages");

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body), JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        log.debug("Calling Groq API with model={}", model);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                log.error("Groq API error: {} — {}", response.code(), errorBody);
                throw new IOException("Groq API call failed: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode json = objectMapper.readTree(responseBody);
            return json.path("choices").get(0)
                    .path("message").path("content").asText().trim();
        }
    }
}