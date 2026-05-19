package com.backend.util;

import com.backend.model.RewriteRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String SYSTEM_BASE =
            "You are an AI writing assistant in an Android keyboard. " +
                    "Return ONLY the rewritten text — no explanations, no quotes, no preamble. " +
                    "Keep the same language as the input.";

    public String buildSystemPrompt(RewriteRequest request) {
        StringBuilder sb = new StringBuilder(SYSTEM_BASE);
        if (request.getAppContext() != null)
            sb.append(" App: ").append(request.getAppContext()).append(".");
        if (request.getContext() != null && !request.getContext().isBlank())
            sb.append(" Context: ").append(request.getContext());
        return sb.toString();
    }

    public String buildUserPrompt(RewriteRequest request) {
        String tone   = request.getTone()   == null ? "professional" : request.getTone();
        String action = request.getAction() == null ? "rewrite"      : request.getAction();
        String text   = request.getText();

        return switch (action) {
            case "grammar"   -> "Fix grammar and spelling only.\n\nText: " + text;
            case "shorten"   -> "Shorten this. Keep core message only.\n\nText: " + text;
            case "expand"    -> "Expand into a fuller message. Tone: " + tone + ".\n\nText: " + text;
            case "email"     -> "Write a complete email. Line 1: Subject: ...\n\nNotes: " + text;
            case "bullet"    -> "Convert to clear bullet points.\n\nText: " + text;
            case "casual"    -> "Rewrite casually and conversationally.\n\nText: " + text;
            case "summarize" -> "Summarize in 2-3 sentences.\n\nText: " + text;
            case "subject"   -> "Write a concise email subject line.\n\nText: " + text;

            // ── Reply: 3 numbered options in ONE call ────────────
            case "reply" ->
                    "Write exactly 3 reply options for this message. " +
                            "Format: 1. [reply]\n2. [reply]\n3. [reply]\n" +
                            "Vary: one formal, one friendly, one brief. No extra text.\n\nMessage: " + text;

            default -> buildTonePrompt(text, tone);
        };
    }

    private String buildTonePrompt(String text, String toneRaw) {
        String[] tones = toneRaw.split(",");
        StringBuilder instr = new StringBuilder("Rewrite this text");
        if (tones.length == 1) {
            instr.append(" in a ").append(desc(tones[0].trim())).append(" tone.");
        } else {
            instr.append(" combining: ");
            for (String t : tones) instr.append(desc(t.trim())).append(", ");
        }
        return instr + "\n\nText: " + text;
    }

    private String desc(String tone) {
        return switch (tone) {
            case "professional" -> "professional and clear";
            case "friendly"     -> "warm and friendly";
            case "formal"       -> "formal and official";
            case "short"        -> "very brief and concise";
            case "confident"    -> "confident and assertive";
            case "gen_z"        -> "casual Gen Z with modern slang";
            case "apology"      -> "sincere and apologetic";
            case "flirty"       -> "light and playful";
            case "empathetic"   -> "empathetic and understanding";
            case "assertive"    -> "direct and assertive";
            default             -> tone;
        };
    }
}