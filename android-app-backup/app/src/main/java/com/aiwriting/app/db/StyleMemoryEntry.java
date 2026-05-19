package com.aiwriting.app.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "style_memory")
public class StyleMemoryEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String originalText;
    public String rewrittenText;
    public String tone;
    public String action;
    public String appContext;
    public long timestamp;

    public StyleMemoryEntry(String originalText, String rewrittenText,
                            String tone, String action, String appContext) {
        this.originalText  = originalText;
        this.rewrittenText = rewrittenText;
        this.tone          = tone;
        this.action        = action;
        this.appContext    = appContext;
        this.timestamp     = System.currentTimeMillis();
    }
}