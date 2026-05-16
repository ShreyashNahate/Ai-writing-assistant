package com.backend.util;

import com.backend.model.RewriteRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public static final String SYSTEM_BASE =
            "You are an AI writing assistant embedded in an Android keyboard. " +
                    "Return ONLY the rewritten text — no explanations, no quotes, no preamble. " +
                    "Keep the same language as the input. Be concise.";

    public String buildSystemPrompt(RewriteRequest request) {
        StringBuilder sb = new StringBuilder(SYSTEM_BASE);

        if (request.getAppContext() != null) {
            sb.append(" The user is typing in ").append(request.getAppContext()).append(".");
        }
        if (request.getContext() != null && !request.getContext().isBlank()) {
            sb.append(" Conversation context: ").append(request.getContext());
        }

        return sb.toString();
    }

    public String buildUserPrompt(RewriteRequest request) {
        String tone = request.getTone() == null ? "professional" : request.getTone();
        String action = request.getAction() == null ? "rewrite" : request.getAction();
        String text = request.getText();

        return switch (action) {
            case "grammar" ->
                    "Fix grammar and spelling only. Do not change meaning or tone.\n\nText: " + text;

            case "shorten" ->
                    "Make this shorter. Keep the core message. Remove filler words.\n\nText: " + text;

            case "expand" ->
                    "Expand this into a fuller, more detailed message. Tone: " + tone + ".\n\nText: " + text;

            case "email" ->
                    "Convert this into a complete, professional email. " +
                            "Include subject line prefixed with 'Subject:'. Body follows on next line.\n\nNotes: " + text;

            case "reply" ->
                    "Write a smart reply to this message. Tone: " + tone + ". Keep it brief.\n\nMessage: " + text;

            case "rewrite" -> buildTonePrompt(text, tone);

            default -> "Improve this text: " + text;
        };
    }

    private String buildTonePrompt(String text, String tone) {
        String toneInstruction = switch (tone) {
            case "professional"  -> "Rewrite professionally and clearly.";
            case "friendly"      -> "Rewrite in a warm, casual, friendly tone.";
            case "formal"        -> "Rewrite formally, suitable for official communication.";
            case "short"         -> "Rewrite as briefly as possible. One sentence max.";
            case "confident"     -> "Rewrite assertively and confidently.";
            case "gen_z"         -> "Rewrite in a casual Gen Z style. Use modern slang naturally.";
            case "apology"       -> "Rewrite as a sincere, empathetic apology.";
            case "flirty"        -> "Rewrite in a light, playful, flirty tone.";
            default              -> "Rewrite clearly and naturally.";
        };

        return toneInstruction + "\n\nText: " + text;
    }

    /**
     * Builds prompt for generating 3 reply suggestions
     */
    public String buildSuggestionsPrompt(String message, String appContext) {
        return "Generate exactly 3 short reply options for this message. " +
                "Return them as a numbered list (1. 2. 3.). " +
                "Vary the tone: one formal, one casual, one brief.\n\n" +
                "App: " + (appContext != null ? appContext : "chat") + "\n" +
                "Message: " + message;
    }
}