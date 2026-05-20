package com.backend.service;

import com.backend.model.UserProfile;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
// @Service
@RequiredArgsConstructor
public class FirebaseUserService {

    private final Firestore firestore;
    private static final String USERS_COLLECTION = "users";

    /**
     * Get or create user profile in Firestore on first login
     */
    public UserProfile getOrCreateUser(String uid, String email) {
        try {
            DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(uid);
            DocumentSnapshot snapshot = docRef.get().get();

            if (snapshot.exists()) {
                UserProfile profile = snapshot.toObject(UserProfile.class);
                // Update last active
                docRef.update("lastActiveAt", System.currentTimeMillis());
                return profile;
            } else {
                // New user — create profile
                UserProfile newUser = UserProfile.builder()
                        .uid(uid)
                        .email(email)
                        .pro(false)
                        .dailyUsageCount(0)
                        .preferredTone("professional")
                        .createdAt(System.currentTimeMillis())
                        .lastActiveAt(System.currentTimeMillis())
                        .build();

                docRef.set(newUser).get();
                log.info("Created new user profile for uid={}", uid);
                return newUser;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error accessing Firestore for uid={}: {}", uid, e.getMessage());
            throw new RuntimeException("Firestore error: " + e.getMessage(), e);
        }
    }

    /**
     * Increment daily usage counter; returns updated count
     */
    public int incrementUsage(String uid) {
        try {
            DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(uid);

            // Increment usage count
            docRef.update(
                    "dailyUsageCount", FieldValue.increment(1),
                    "lastActiveAt", System.currentTimeMillis()
            ).get();

            // Fetch updated document
            DocumentSnapshot snapshot = docRef.get().get();

            Long count = snapshot.getLong("dailyUsageCount");

            return count != null ? count.intValue() : 0;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error incrementing usage for uid={}", uid, e);
            return 0;
        }
    }

    /**
     * Get current daily usage count
     */
    public int getDailyUsage(String uid) {
        try {
            DocumentSnapshot snapshot = firestore
                    .collection(USERS_COLLECTION).document(uid).get().get();
            Long count = snapshot.getLong("dailyUsageCount");
            return count != null ? count.intValue() : 0;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting usage for uid={}", uid, e);
            return 0;
        }
    }

    /**
     * Update user's preferred tone
     */
    public void updatePreferredTone(String uid, String tone) {
        firestore.collection(USERS_COLLECTION).document(uid)
                .update("preferredTone", tone);
    }

    /**
     * Update writing style memory (learned from usage)
     */
    public void updateWritingStyle(String uid, String styleDescription) {
        firestore.collection(USERS_COLLECTION).document(uid)
                .update("writingStyle", styleDescription);
    }

    /**
     * Upgrade user to Pro (called after payment confirmed)
     */
    public void upgradeToPro(String uid) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("pro", true);
            updates.put("upgradedAt", System.currentTimeMillis());
            firestore.collection(USERS_COLLECTION).document(uid).update(updates).get();
            log.info("User {} upgraded to Pro", uid);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error upgrading uid={} to Pro", uid, e);
        }
    }

    /**
     * Save a rewrite history entry for the user
     */
    public void saveHistoryEntry(String uid, String original, String rewritten, String tone) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("original", original);
        entry.put("rewritten", rewritten);
        entry.put("tone", tone);
        entry.put("timestamp", System.currentTimeMillis());

        firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection("history")
                .add(entry);
    }
}
