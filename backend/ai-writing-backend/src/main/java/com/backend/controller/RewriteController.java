package com.backend.controller;

import com.backend.service.FirebaseUserService;
import com.backend.model.RewriteRequest;
import com.backend.model.RewriteResponse;
import com.backend.model.UserProfile;
import com.backend.security.AuthenticatedUser;
import com.backend.service.RewriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RewriteController {

    private final RewriteService rewriteService;
    private final FirebaseUserService userService;

    /** Health check */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "ai-writing-backend"));
    }

    @GetMapping("/")
    public String home() {
        return "AI Writing Backend Running";
    }
    /**
     * Main rewrite endpoint — called by Android app on every AI request
     * POST /api/rewrite
     * Header: Authorization: Bearer <Firebase ID Token>
     */
    @PostMapping("/rewrite")
    public ResponseEntity<RewriteResponse> rewrite(
            @Valid @RequestBody RewriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        log.info("Rewrite request from uid={} action={} tone={}",
                user.getUid(), request.getAction(), request.getTone());

        RewriteResponse response = rewriteService.rewrite(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Get user profile — called on app start
     * GET /api/user/profile
     */
    @GetMapping("/user/profile")
    public ResponseEntity<UserProfile> getProfile(
            @AuthenticationPrincipal AuthenticatedUser user) {

        UserProfile profile = userService.getOrCreateUser(user.getUid(), user.getEmail());
        return ResponseEntity.ok(profile);
    }

    /**
     * Update preferred tone
     * PUT /api/user/tone
     */
    @PutMapping("/user/tone")
    public ResponseEntity<Map<String, String>> updateTone(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String tone = body.get("tone");
        if (tone == null || tone.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "tone is required"));
        }
        userService.updatePreferredTone(user.getUid(), tone);
        return ResponseEntity.ok(Map.of("updated", tone));
    }

    /**
     * Get usage stats
     * GET /api/user/usage
     */
    @GetMapping("/user/usage")
    public ResponseEntity<Map<String, Object>> getUsage(
            @AuthenticationPrincipal AuthenticatedUser user) {

        int usage = userService.getDailyUsage(user.getUid());
        int limit = user.isPro() ? 500 : 30;
        return ResponseEntity.ok(Map.of(
                "dailyUsage", usage,
                "dailyLimit", limit,
                "isPro", user.isPro(),
                "remaining", Math.max(0, limit - usage)
        ));
    }

    /** Global exception handler */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        log.error("API error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
