package com.aiwriting.app.model;

public class UserProfile {
    private String uid;
    private String email;
    private String displayName;
    private boolean pro;
    private int dailyUsageCount;
    private String preferredTone;
    private String writingStyle;
    private long createdAt;
    private long lastActiveAt;

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isPro() { return pro; }
    public void setPro(boolean pro) { this.pro = pro; }
    public int getDailyUsageCount() { return dailyUsageCount; }
    public void setDailyUsageCount(int dailyUsageCount) { this.dailyUsageCount = dailyUsageCount; }
    public String getPreferredTone() { return preferredTone; }
    public void setPreferredTone(String preferredTone) { this.preferredTone = preferredTone; }
    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String writingStyle) { this.writingStyle = writingStyle; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}

