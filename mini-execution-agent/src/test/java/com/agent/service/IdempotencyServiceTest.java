package com.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
    }

    @Test
    @DisplayName("Should return false for non-executed ID")
    void shouldReturnFalseForNonExecutedId() {
        // Given
        String executionId = "exec-test-001";

        // When
        boolean result = idempotencyService.alreadyExecuted(executionId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false after marking as executed with different status")
    void shouldReturnFalseAfterMarkingAsExecutedWithDifferentStatus() {
        // Given
        String executionId = "exec-test-001";
        LocalDateTime executedAt = LocalDateTime.now();
        
        // Mark with non-completed status
        Map<String, String> entry = new ConcurrentHashMap<>();
        entry.put("status", "FAILED");
        entry.put("executed_at", executedAt.toString());
        
        // Access the internal state directly for testing
        Map<String, Map<String, String>> state = idempotencyService.getAllExecutions();
        state.put(executionId, entry);

        // When
        boolean result = idempotencyService.alreadyExecuted(executionId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return true for executed ID")
    void shouldReturnTrueForExecutedId() {
        // Given
        String executionId = "exec-test-001";
        LocalDateTime executedAt = LocalDateTime.now();
        idempotencyService.markExecuted(executionId, executedAt);

        // When
        boolean result = idempotencyService.alreadyExecuted(executionId);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should mark execution as completed")
    void shouldMarkExecutionAsCompleted() {
        // Given
        String executionId = "exec-test-001";
        LocalDateTime executedAt = LocalDateTime.of(2025, 2, 28, 10, 0, 0);

        // When
        idempotencyService.markExecuted(executionId, executedAt);

        // Then
        assertTrue(idempotencyService.alreadyExecuted(executionId));
        
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        assertTrue(allExecutions.containsKey(executionId));
        
        Map<String, String> execution = allExecutions.get(executionId);
        assertEquals("COMPLETED", execution.get("status"));
        assertEquals(executedAt.toString(), execution.get("executed_at"));
    }

    @Test
    @DisplayName("Should handle multiple executions")
    void shouldHandleMultipleExecutions() {
        // Given
        String executionId1 = "exec-test-001";
        String executionId2 = "exec-test-002";
        LocalDateTime executedAt1 = LocalDateTime.of(2025, 2, 28, 10, 0, 0);
        LocalDateTime executedAt2 = LocalDateTime.of(2025, 2, 28, 11, 0, 0);

        // When
        idempotencyService.markExecuted(executionId1, executedAt1);
        idempotencyService.markExecuted(executionId2, executedAt2);

        // Then
        assertTrue(idempotencyService.alreadyExecuted(executionId1));
        assertTrue(idempotencyService.alreadyExecuted(executionId2));
        
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        assertEquals(2, allExecutions.size());
        assertTrue(allExecutions.containsKey(executionId1));
        assertTrue(allExecutions.containsKey(executionId2));
    }

    @Test
    @DisplayName("Should return unmodifiable map of all executions")
    void shouldReturnUnmodifiableMapOfAllExecutions() {
        // Given
        String executionId = "exec-test-001";
        idempotencyService.markExecuted(executionId, LocalDateTime.now());

        // When
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();

        // Then
        assertNotNull(allExecutions);
        assertEquals(1, allExecutions.size());
        assertTrue(allExecutions.containsKey(executionId));

        // Verify it's unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            allExecutions.put("new-id", new ConcurrentHashMap<>());
        });
    }

    @Test
    @DisplayName("Should return empty map when no executions exist")
    void shouldReturnEmptyMapWhenNoExecutionsExist() {
        // When
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();

        // Then
        assertNotNull(allExecutions);
        assertTrue(allExecutions.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> {
            allExecutions.put("new-id", new ConcurrentHashMap<>());
        });
    }

    @Test
    @DisplayName("Should handle null execution ID gracefully")
    void shouldHandleNullExecutionIdGracefully() {
        // When
        boolean result = idempotencyService.alreadyExecuted(null);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle empty execution ID gracefully")
    void shouldHandleEmptyExecutionIdGracefully() {
        // Given
        String emptyExecutionId = "";

        // When
        boolean result = idempotencyService.alreadyExecuted(emptyExecutionId);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle marking null execution ID")
    void shouldHandleMarkingNullExecutionId() {
        // Given
        String nullExecutionId = null;
        LocalDateTime executedAt = LocalDateTime.now();

        // When & Then
        assertDoesNotThrow(() -> {
            idempotencyService.markExecuted(nullExecutionId, executedAt);
        });

        // Verify it doesn't affect the state
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        assertTrue(allExecutions.isEmpty());
    }

    @Test
    @DisplayName("Should handle overwriting existing execution")
    void shouldHandleOverwritingExistingExecution() {
        // Given
        String executionId = "exec-test-001";
        LocalDateTime firstExecutedAt = LocalDateTime.of(2025, 2, 28, 10, 0, 0);
        LocalDateTime secondExecutedAt = LocalDateTime.of(2025, 2, 28, 11, 0, 0);

        // When
        idempotencyService.markExecuted(executionId, firstExecutedAt);
        assertTrue(idempotencyService.alreadyExecuted(executionId));
        
        // Overwrite with new timestamp
        idempotencyService.markExecuted(executionId, secondExecutedAt);

        // Then
        assertTrue(idempotencyService.alreadyExecuted(executionId));
        
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        Map<String, String> execution = allExecutions.get(executionId);
        assertEquals("COMPLETED", execution.get("status"));
        assertEquals(secondExecutedAt.toString(), execution.get("executed_at")); // Should be updated
    }

    @Test
    @DisplayName("Should handle concurrent access")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        // Given
        String executionId = "exec-concurrent-test";
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When - simulate concurrent access
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                String threadSpecificId = executionId + "-" + threadIndex;
                idempotencyService.markExecuted(threadSpecificId, LocalDateTime.now());
                assertTrue(idempotencyService.alreadyExecuted(threadSpecificId));
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        assertEquals(threadCount, allExecutions.size());
        
        // Verify all thread-specific executions are marked
        for (int i = 0; i < threadCount; i++) {
            String threadSpecificId = executionId + "-" + i;
            assertTrue(idempotencyService.alreadyExecuted(threadSpecificId));
        }
    }

    @Test
    @DisplayName("Should preserve execution order")
    void shouldPreserveExecutionOrder() throws InterruptedException {
        // Given
        String[] executionIds = {"exec-001", "exec-002", "exec-003"};
        LocalDateTime[] executionTimes = {
            LocalDateTime.of(2025, 2, 28, 10, 0, 0),
            LocalDateTime.of(2025, 2, 28, 11, 0, 0),
            LocalDateTime.of(2025, 2, 28, 12, 0, 0)
        };

        // When - add executions in order
        for (int i = 0; i < executionIds.length; i++) {
            idempotencyService.markExecuted(executionIds[i], executionTimes[i]);
        }

        // Then
        Map<String, Map<String, String>> allExecutions = idempotencyService.getAllExecutions();
        assertEquals(3, allExecutions.size());
        
        // Verify timestamps are preserved correctly
        for (int i = 0; i < executionIds.length; i++) {
            Map<String, String> execution = allExecutions.get(executionIds[i]);
            assertEquals(executionTimes[i].toString(), execution.get("executed_at"));
        }
    }
}
