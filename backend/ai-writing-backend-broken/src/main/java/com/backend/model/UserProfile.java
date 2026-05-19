package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String uid;
    private String email;
    private String displayName;
    private boolean pro;
    private int dailyUsageCount;
    private String preferredTone;        // default tone preference
    private String writingStyle;         // learned style description
    private Map<String, Object> metadata;
    private String lastResetDate;
    private long createdAt;
    private long lastActiveAt;
}