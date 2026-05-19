package com.backend.service;

import com.backend.model.UserProfile;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseUserService {

    private final Firestore firestore;
    private static final String USERS = "users";

    public UserProfile getOrCreateUser(String uid, String email) {
        try {
            DocumentReference ref = firestore.collection(USERS).document(uid);
            DocumentSnapshot snap = ref.get().get();
            if (snap.exists()) {
                ref.update("lastActiveAt", System.currentTimeMillis());
                return snap.toObject(UserProfile.class);
            }
            UserProfile user = UserProfile.builder()
                    .uid(uid).email(email).pro(false)
                    .dailyUsageCount(0)
                    .preferredTone("professional")
                    .lastResetDate(todayString())         // ← store today
                    .createdAt(System.currentTimeMillis())
                    .lastActiveAt(System.currentTimeMillis())
                    .build();
            ref.set(user).get();
            return user;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error", e);
        }
    }

    /**
     * Returns current daily usage — resets to 0 if it's a new day.
     */
    public int getDailyUsage(String uid) {
        try {
            DocumentReference ref = firestore.collection(USERS).document(uid);
            DocumentSnapshot snap = ref.get().get();

            String lastReset = snap.getString("lastResetDate");
            String today     = todayString();

            // New day → reset counter
            if (!today.equals(lastReset)) {
                ref.update(
                        "dailyUsageCount", 0,
                        "lastResetDate",   today
                ).get();
                log.info("Daily usage reset for uid={}", uid);
                return 0;
            }

            Long count = snap.getLong("dailyUsageCount");
            return count != null ? count.intValue() : 0;

        } catch (InterruptedException | ExecutionException e) {
            log.error("getDailyUsage error uid={}", uid, e);
            return 0;
        }
    }

    public int incrementUsage(String uid) {
        try {
            DocumentReference ref = firestore.collection(USERS).document(uid);
            ref.update(
                    "dailyUsageCount", FieldValue.increment(1),
                    "lastActiveAt",    System.currentTimeMillis()
            ).get();
            return getDailyUsage(uid);
        } catch (InterruptedException | ExecutionException e) {
            log.error("incrementUsage error uid={}", uid, e);
            return 0;
        }
    }

    public void updatePreferredTone(String uid, String tone) {
        firestore.collection(USERS).document(uid).update("preferredTone", tone);
    }

    public void upgradeToPro(String uid) {
        try {
            Map<String, Object> u = new HashMap<>();
            u.put("pro", true);
            u.put("upgradedAt", System.currentTimeMillis());
            firestore.collection(USERS).document(uid).update(u).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("upgradeToPro error uid={}", uid, e);
        }
    }

    public void saveHistoryEntry(String uid, String original, String rewritten, String tone) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("original",   original);
        entry.put("rewritten",  rewritten);
        entry.put("tone",       tone);
        entry.put("timestamp",  System.currentTimeMillis());
        firestore.collection(USERS).document(uid).collection("history").add(entry);
    }

    /** Returns today as "YYYY-MM-DD" in IST */
    private String todayString() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
    }
}