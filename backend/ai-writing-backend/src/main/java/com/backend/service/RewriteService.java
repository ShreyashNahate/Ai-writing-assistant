package com.backend.service;

import com.backend.service.FirebaseUserService;
import com.backend.model.RewriteRequest;
import com.backend.model.RewriteResponse;
import com.backend.security.AuthenticatedUser;
import com.backend.util.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
// @Service
@RequiredArgsConstructor
public class RewriteService {

    private static final int FREE_DAILY_LIMIT = 30;
    private static final int PRO_DAILY_LIMIT = 500;

    private final GroqService groqService;
    private final PromptBuilder promptBuilder;
    private final FirebaseUserService userService;

    public RewriteResponse rewrite(RewriteRequest request, AuthenticatedUser user) {
        // Check daily usage limit
        int usage = userService.getDailyUsage(user.getUid());
        int limit = user.isPro() ? PRO_DAILY_LIMIT : FREE_DAILY_LIMIT;

        if (usage >= limit) {
            throw new RuntimeException("Daily limit reached. Upgrade to Pro for more.");
        }

        long start = System.currentTimeMillis();

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt(request);
            String userPrompt = promptBuilder.buildUserPrompt(request);

            // Use smart model for email generation, fast for everything else
            boolean useSmartModel = "email".equals(request.getAction());
            String result = groqService.complete(systemPrompt, userPrompt, useSmartModel);

            // For reply suggestions, parse the numbered list into 3 options
            List<String> suggestions = new ArrayList<>();
            if ("reply".equals(request.getAction())) {
                String suggestionsRaw = groqService.complete(
                        promptBuilder.buildSystemPrompt(request),
                        promptBuilder.buildSuggestionsPrompt(request.getText(), request.getAppContext()),
                        false
                );
                suggestions = parseSuggestions(suggestionsRaw);
            }

            long latencyMs = System.currentTimeMillis() - start;

            // Increment usage + save history asynchronously
            userService.incrementUsage(user.getUid());
            userService.saveHistoryEntry(user.getUid(), request.getText(), result, request.getTone());

            log.info("Rewrite completed for uid={} action={} latency={}ms",
                    user.getUid(), request.getAction(), latencyMs);

            return RewriteResponse.builder()
                    .original(request.getText())
                    .rewritten(result)
                    .suggestions(suggestions)
                    .tone(request.getTone())
                    .action(request.getAction())
                    .latencyMs(latencyMs)
                    .model(useSmartModel ? "llama-3.3-70b-versatile" : "llama-3.1-8b-instant")
                    .build();

        } catch (IOException e) {
            log.error("Groq API error for uid={}: {}", user.getUid(), e.getMessage());
            throw new RuntimeException("AI service temporarily unavailable. Please try again.");
        }
    }

    private List<String> parseSuggestions(String raw) {
        // Parse "1. text\n2. text\n3. text" into a list
        return Arrays.stream(raw.split("\n"))
                .filter(line -> line.matches("^[123]\\. .+"))
                .map(line -> line.replaceFirst("^[123]\\. ", "").trim())
                .limit(3)
                .toList();
    }
}
