package com.agent.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store for prototype usage.
 */
@Service
public class IdempotencyService {

    private final Map<String, Map<String, String>> state = new ConcurrentHashMap<>();

    public boolean alreadyExecuted(String executionId) {
        Map<String, String> entry = state.get(executionId);
        return entry != null && "COMPLETED".equals(entry.get("status"));
    }

    public void markExecuted(String executionId, LocalDateTime executedAt) {
        Map<String, String> entry = new HashMap<>();
        entry.put("status", "COMPLETED");
        entry.put("executed_at", executedAt.toString());
        state.put(executionId, entry);
    }

    public Map<String, Map<String, String>> getAllExecutions() {
        return Collections.unmodifiableMap(state);
    }
}
