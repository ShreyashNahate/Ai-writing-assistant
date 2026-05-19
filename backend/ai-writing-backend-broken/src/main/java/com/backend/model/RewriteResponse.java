package com.backend.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RewriteResponse {
    private String original;
    private String rewritten;
    private List<String> suggestions;   // up to 3 variants
    private String tone;
    private String action;
    private long latencyMs;
    private String model;
}