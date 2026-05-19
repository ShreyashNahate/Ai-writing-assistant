package com.aiwriting.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StyleMemoryDao {

    @Insert
    void insert(StyleMemoryEntry entry);

    /** Get last N rewrites — used to analyze user's writing style */
    @Query("SELECT * FROM style_memory ORDER BY timestamp DESC LIMIT :limit")
    List<StyleMemoryEntry> getRecent(int limit);

    /** Get rewrites filtered by app (e.g. whatsapp style vs gmail style) */
    @Query("SELECT * FROM style_memory WHERE appContext = :app ORDER BY timestamp DESC LIMIT :limit")
    List<StyleMemoryEntry> getRecentByApp(String app, int limit);

    @Query("SELECT COUNT(*) FROM style_memory")
    int getTotalCount();

    @Query("DELETE FROM style_memory WHERE timestamp < :beforeTimestamp")
    void deleteOlderThan(long beforeTimestamp);

    @Query("DELETE FROM style_memory")
    void clearAll();
}