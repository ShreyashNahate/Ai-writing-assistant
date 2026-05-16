package com.aiwriting.app.model;

public class UsageStats {
    private int dailyUsage;
    private int dailyLimit;
    private boolean isPro;
    private int remaining;

    public int getDailyUsage() { return dailyUsage; }
    public void setDailyUsage(int dailyUsage) { this.dailyUsage = dailyUsage; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    public boolean isPro() { return isPro; }
    public void setPro(boolean pro) { isPro = pro; }
    public int getRemaining() { return remaining; }
    public void setRemaining(int remaining) { this.remaining = remaining; }
}