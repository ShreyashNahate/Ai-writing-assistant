package com.aiwriting.app.model;

// ── RewriteRequest ──────────────────────────────────────────────
public class RewriteRequest {
    private String text;
    private String tone = "professional";
    private String action = "rewrite";
    private String context;
    private String appContext;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getAppContext() { return appContext; }
    public void setAppContext(String appContext) { this.appContext = appContext; }
}
