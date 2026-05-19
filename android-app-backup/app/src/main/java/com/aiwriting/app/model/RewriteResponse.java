package com.aiwriting.app.model;


import java.util.List;

public class RewriteResponse {
    private String original;
    private String rewritten;
    private List<String> suggestions;
    private String tone;
    private String action;
    private long latencyMs;
    private String model;

    public String getOriginal() { return original; }
    public String getRewritten() { return rewritten; }
    public List<String> getSuggestions() { return suggestions; }
    public String getTone() { return tone; }
    public String getAction() { return action; }
    public long getLatencyMs() { return latencyMs; }
    public String getModel() { return model; }
}