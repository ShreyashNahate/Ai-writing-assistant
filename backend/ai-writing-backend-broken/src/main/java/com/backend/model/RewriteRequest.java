package com.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RewriteRequest {

    @NotBlank(message = "Text cannot be blank")
    @Size(max = 2000, message = "Text too long (max 2000 chars)")
    private String text;

    // professional, friendly, short, formal, gen_z, apology
    private String tone = "professional";

    // rewrite, grammar, shorten, expand, email, reply
    private String action = "rewrite";

    // Optional: visible screen context (from AccessibilityService)
    private String context;

    // App the user is typing in (whatsapp, gmail, linkedin, etc.)
    private String appContext;
}