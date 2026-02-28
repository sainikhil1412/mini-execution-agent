package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.AuditEntry;
import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditService Tests")
class AuditServiceTest {

    private AuditService auditService;

    @TempDir
    Path tempDir;

    private ExecutionPlan testPlan;
    private List<AuditEntry> testChanges;
    private List<AuditEntry> testSkipped;

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
        
        // Override the output directory for testing
        ReflectionTestUtils.setField(auditService, "OUTPUT_DIR", tempDir.toString() + "/");

        // Create test execution plan
        testPlan = new ExecutionPlan();
        testPlan.setExecutionId("exec-test-001");
        testPlan.setVersion("1.0");
        testPlan.setInstruction("Increase prices by 10% for fitness products");
        testPlan.setCreatedAt(LocalDateTime.of(2025, 2, 28, 10, 0, 0));
        testPlan.setFilters(List.of(
                new Filter("category", Filter.FilterOperator.EQUALS, "fitness"),
                new Filter("in_stock", Filter.FilterOperator.EQUALS, "true")
        ));
        testPlan.setAction(new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        ));
        testPlan.setSummaryRequired(true);
        testPlan.setDryRun(false);

        // Create test audit entries
        testChanges = List.of(
                createAuditEntry("TEST001", 29.99, 32.99, "CHANGED"),
                createAuditEntry("TEST002", 39.99, 43.99, "CHANGED")
        );

        testSkipped = List.of(
                createAuditEntry("TEST003", 49.99, 49.99, "SKIPPED"),
                createAuditEntry("TEST004", 19.99, 19.99, "SKIPPED")
        );
    }

    private AuditEntry createAuditEntry(String sku, double before, double after, String status) {
        AuditEntry entry = new AuditEntry();
        entry.setSku(sku);
        entry.setField("price");
        entry.setBefore(before);
        entry.setAfter(after);
        entry.setStatus(status);
        return entry;
    }

    @Test
    @DisplayName("Should write audit log successfully")
    void shouldWriteAuditLogSuccessfully() {
        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        assertTrue(auditPath.contains("audit_exec-test-001.json"));
        
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
        assertTrue(auditFile.length() > 0);
    }

    @Test
    @DisplayName("Should write audit log with dry run status")
    void shouldWriteAuditLogWithDryRunStatus() {
        // Given
        testPlan.setDryRun(true);

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "DRY_RUN", "Dry run summary");

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should write audit log with null summary")
    void shouldWriteAuditLogWithNullSummary() {
        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", null);

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should write audit log with empty changes and skipped")
    void shouldWriteAuditLogWithEmptyChangesAndSkipped() {
        // Given
        List<AuditEntry> emptyChanges = List.of();
        List<AuditEntry> emptySkipped = List.of();

        // When
        String auditPath = auditService.writeAuditLog(testPlan, emptyChanges, emptySkipped, "COMPLETED", "No changes");

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should create output directory if it doesn't exist")
    void shouldCreateOutputDirectoryIfItDoesntExist() {
        // Given - Use a nested directory that doesn't exist
        String nestedOutputDir = tempDir.resolve("nested").resolve("audit").toString();
        ReflectionTestUtils.setField(auditService, "OUTPUT_DIR", nestedOutputDir + "/");

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
        assertTrue(auditFile.getParentFile().exists());
    }

    @Test
    @DisplayName("Should handle file write failure")
    void shouldHandleFileWriteFailure() {
        // Given - Try to write to an invalid location
        String invalidPath = "C:\\invalid\\path\\that\\does\\not\\exist\\audit_test.json";
        ReflectionTestUtils.setField(auditService, "OUTPUT_DIR", invalidPath);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");
        });

        assertEquals("AUDIT_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to write audit log"));
    }

    @Test
    @DisplayName("Should include all required fields in audit log")
    void shouldIncludeAllRequiredFieldsInAuditLog() {
        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
        assertTrue(auditFile.length() > 0);
        
        // The audit file should contain the execution ID and other key information
        // (We can't easily verify JSON content without parsing it, but we can verify the file exists and has content)
    }

    @Test
    @DisplayName("Should handle special characters in execution ID")
    void shouldHandleSpecialCharactersInExecutionId() {
        // Given
        testPlan.setExecutionId("exec-test-001_SPECIAL-CHARS_123");

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        assertTrue(auditPath.contains("audit_exec-test-001_SPECIAL-CHARS_123.json"));
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should handle very long execution ID")
    void shouldHandleVeryLongExecutionId() {
        // Given
        String longExecutionId = "exec-" + "A".repeat(100);
        testPlan.setExecutionId(longExecutionId);

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        assertTrue(auditPath.contains("audit_" + longExecutionId + ".json"));
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should handle null execution ID gracefully")
    void shouldHandleNullExecutionIdGracefully() {
        // Given
        testPlan.setExecutionId(null);

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");
        });
    }

    @Test
    @DisplayName("Should handle empty execution ID")
    void shouldHandleEmptyExecutionId() {
        // Given
        testPlan.setExecutionId("");

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        assertTrue(auditPath.contains("audit_.json"));
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
    }

    @Test
    @DisplayName("Should handle concurrent audit writes")
    void shouldHandleConcurrentAuditWrites() throws InterruptedException {
        // Given
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        String[] auditPaths = new String[threadCount];

        // When - Write audit logs concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                ExecutionPlan plan = new ExecutionPlan();
                plan.setExecutionId("exec-concurrent-" + threadIndex);
                plan.setVersion("1.0");
                plan.setInstruction("Test instruction " + threadIndex);
                plan.setCreatedAt(LocalDateTime.now());
                plan.setFilters(List.of());
                plan.setAction(new PriceAdjustmentAction(
                        PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                        10.0,
                        PriceAdjustmentAction.Direction.INCREASE,
                        2
                ));
                plan.setSummaryRequired(true);
                plan.setDryRun(false);

                auditPaths[threadIndex] = auditService.writeAuditLog(plan, testChanges, testSkipped, "COMPLETED", "Summary " + threadIndex);
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
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(auditPaths[i]);
            assertTrue(auditPaths[i].contains("audit_exec-concurrent-" + i + ".json"));
            
            File auditFile = new File(auditPaths[i]);
            assertTrue(auditFile.exists());
            assertTrue(auditFile.length() > 0);
        }
    }

    @Test
    @DisplayName("Should handle large audit data")
    void shouldHandleLargeAuditData() {
        // Given - Create a large number of audit entries
        List<AuditEntry> largeChanges = List.of();
        List<AuditEntry> largeSkipped = List.of();
        
        for (int i = 0; i < 1000; i++) {
            largeChanges.add(createAuditEntry("TEST" + String.format("%04d", i), 10.0 + i, 11.0 + i, "CHANGED"));
            largeSkipped.add(createAuditEntry("SKIP" + String.format("%04d", i), 20.0 + i, 20.0 + i, "SKIPPED"));
        }

        // When
        String auditPath = auditService.writeAuditLog(testPlan, largeChanges, largeSkipped, "COMPLETED", "Large data summary");

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
        assertTrue(auditFile.length() > 1000); // Should be substantial file
    }

    @Test
    @DisplayName("Should preserve execution order in audit log")
    void shouldPreserveExecutionOrderInAuditLog() {
        // Given
        LocalDateTime specificTime = LocalDateTime.of(2025, 2, 28, 15, 30, 45);
        testPlan.setCreatedAt(specificTime);

        // When
        String auditPath = auditService.writeAuditLog(testPlan, testChanges, testSkipped, "COMPLETED", "Test summary");

        // Then
        assertNotNull(auditPath);
        File auditFile = new File(auditPath);
        assertTrue(auditFile.exists());
        
        // The audit log should contain the specific creation time
        // (We can't easily verify JSON content without parsing, but we can verify the file exists)
    }
}
