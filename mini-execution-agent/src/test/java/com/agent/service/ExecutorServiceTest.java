package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.executor.ActionHandler;
import com.agent.model.*;
import com.agent.validator.PlanValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutorService Tests")
class ExecutorServiceTest {

    @Mock
    private PlanValidator planValidator;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private CsvService csvService;

    @Mock
    private AuditService auditService;

    @Mock
    private ActionHandler actionHandler;

    @InjectMocks
    private ExecutorService executorService;

    @TempDir
    Path tempDir;

    private ExecutionPlan validPlan;
    private List<ProductRecord> testRecords;
    private List<AuditEntry> mockChanges;
    private List<AuditEntry> mockSkipped;

    @BeforeEach
    void setUp() {
        // Set up test file paths
        ReflectionTestUtils.setField(executorService, "inputCsvPath", 
                tempDir.resolve("test_input.csv").toString());
        ReflectionTestUtils.setField(executorService, "outputCsvPath", 
                tempDir.resolve("test_output.csv").toString());

        // Create valid execution plan
        validPlan = new ExecutionPlan();
        validPlan.setExecutionId("exec-test-001");
        validPlan.setVersion("1.0");
        validPlan.setInstruction("Increase prices by 10% for all in-stock fitness products");
        validPlan.setCreatedAt(LocalDateTime.now());
        validPlan.setFilters(List.of(
                new Filter("category", Filter.FilterOperator.EQUALS, "fitness"),
                new Filter("in_stock", Filter.FilterOperator.EQUALS, "true")
        ));
        validPlan.setAction(new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        ));
        validPlan.setSummaryRequired(true);
        validPlan.setDryRun(false);

        // Create test product records
        testRecords = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true),
                new ProductRecord("TEST002", "fitness", 39.99, true),
                new ProductRecord("TEST003", "fitness", 49.99, false),
                new ProductRecord("TEST004", "yoga", 19.99, true)
        );

        // Create mock audit entries
        mockChanges = List.of(
                createAuditEntry("TEST001", 29.99, 32.99, "CHANGED"),
                createAuditEntry("TEST002", 39.99, 43.99, "CHANGED")
        );

        mockSkipped = List.of(
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
    @DisplayName("Should execute plan successfully")
    void shouldExecutePlanSuccessfully() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.apply(any(), any(), anyBoolean())).thenReturn(mockChanges.get(0), mockChanges.get(1));
        when(actionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0), mockSkipped.get(1));
        when(actionHandler.buildSummary(any(), any(), any(), anyBoolean())).thenReturn("2 products updated");
        when(auditService.writeAuditLog(any(), any(), any(), anyString(), anyString())).thenReturn("audit_test.json");

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertEquals("exec-test-001", result.getExecutionId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Execution completed successfully.", result.getMessage());
        assertEquals(4, result.getTotalRecords());
        assertEquals(2, result.getMatchedRecords());
        assertEquals(2, result.getChangedRecords());
        assertEquals(2, result.getSkippedRecords());
        assertEquals("2 products updated", result.getSummary());
        assertNotNull(result.getExecutedAt());

        verify(planValidator, times(1)).validateExecutionPlan(validPlan);
        verify(idempotencyService, times(1)).alreadyExecuted("exec-test-001");
        verify(csvService, times(1)).readCsv(anyString());
        verify(csvService, times(1)).writeCsvAtomic(eq(testRecords), anyString());
        verify(auditService, times(1)).writeAuditLog(any(), any(), any(), eq("COMPLETED"), anyString());
        verify(idempotencyService, times(1)).markExecuted(eq("exec-test-001"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should skip execution when already executed")
    void shouldSkipExecutionWhenAlreadyExecuted() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(true);

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertEquals("exec-test-001", result.getExecutionId());
        assertEquals("SKIPPED", result.getStatus());
        assertEquals("This execution_id has already been completed.", result.getMessage());

        verify(planValidator, times(1)).validateExecutionPlan(validPlan);
        verify(idempotencyService, times(1)).alreadyExecuted("exec-test-001");
        verify(csvService, never()).readCsv(anyString());
        verify(csvService, never()).writeCsvAtomic(any(), anyString());
        verify(auditService, never()).writeAuditLog(any(), any(), any(), anyString(), anyString());
        verify(idempotencyService, never()).markExecuted(any(), any());
    }

    @Test
    @DisplayName("Should execute dry run successfully")
    void shouldExecuteDryRunSuccessfully() throws Exception {
        // Given
        validPlan.setDryRun(true);
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.apply(any(), any(), eq(true))).thenReturn(mockChanges.get(0), mockChanges.get(1));
        when(actionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0), mockSkipped.get(1));
        when(actionHandler.buildSummary(any(), any(), any(), eq(true))).thenReturn("[DRY RUN] 2 products would be updated");
        when(auditService.writeAuditLog(any(), any(), any(), eq("DRY_RUN"), anyString())).thenReturn("audit_test.json");

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertEquals("exec-test-001", result.getExecutionId());
        assertEquals("DRY_RUN", result.getStatus());
        assertEquals("Dry run completed. No data was modified.", result.getMessage());
        assertEquals("[DRY RUN] 2 products would be updated", result.getSummary());
        assertNull(result.getOutputCsvPath());
        assertNull(result.getOutputCsvName());

        verify(csvService, never()).writeCsvAtomic(any(), anyString());
        verify(auditService, times(1)).writeAuditLog(any(), any(), any(), eq("DRY_RUN"), anyString());
        verify(idempotencyService, times(1)).markExecuted(eq("exec-test-001"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should throw exception when validation fails")
    void shouldThrowExceptionWhenValidationFails() {
        // Given
        doThrow(new AgentException("VALIDATION_ERROR", "Invalid plan"))
                .when(planValidator).validateExecutionPlan(validPlan);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            executorService.execute(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals("Invalid plan", exception.getMessage());

        verify(planValidator, times(1)).validateExecutionPlan(validPlan);
        verify(idempotencyService, never()).alreadyExecuted(any());
        verify(csvService, never()).readCsv(anyString());
    }

    @Test
    @DisplayName("Should throw exception when no action handler found")
    void shouldThrowExceptionWhenNoActionHandlerFound() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(false);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            executorService.execute(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No action handler registered"));

        verify(planValidator, times(1)).validateExecutionPlan(validPlan);
        verify(idempotencyService, times(1)).alreadyExecuted("exec-test-001");
        verify(csvService, times(1)).readCsv(anyString());
    }

    @Test
    @DisplayName("Should handle CSV read failure")
    void shouldHandleCsvReadFailure() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenThrow(new AgentException("CSV_ERROR", "Failed to read CSV"));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            executorService.execute(validPlan);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertEquals("Failed to read CSV", exception.getMessage());

        verify(csvService, times(1)).readCsv(anyString());
        verify(csvService, never()).writeCsvAtomic(any(), anyString());
    }

    @Test
    @DisplayName("Should handle CSV write failure")
    void shouldHandleCsvWriteFailure() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.apply(any(), any(), anyBoolean())).thenReturn(mockChanges.get(0));
        when(actionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0));
        when(actionHandler.buildSummary(any(), any(), any(), anyBoolean())).thenReturn("Summary");
        doThrow(new AgentException("CSV_ERROR", "Failed to write CSV"))
                .when(csvService).writeCsvAtomic(any(), anyString());

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            executorService.execute(validPlan);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertEquals("Failed to write CSV", exception.getMessage());

        verify(csvService, times(1)).writeCsvAtomic(any(), anyString());
    }

    @Test
    @DisplayName("Should handle audit service failure")
    void shouldHandleAuditServiceFailure() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.apply(any(), any(), anyBoolean())).thenReturn(mockChanges.get(0));
        when(actionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0));
        when(actionHandler.buildSummary(any(), any(), any(), anyBoolean())).thenReturn("Summary");
        when(auditService.writeAuditLog(any(), any(), any(), anyString(), anyString()))
                .thenThrow(new AgentException("AUDIT_ERROR", "Failed to write audit log"));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            executorService.execute(validPlan);
        });

        assertEquals("AUDIT_ERROR", exception.getErrorCode());
        assertEquals("Failed to write audit log", exception.getMessage());

        verify(auditService, times(1)).writeAuditLog(any(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle empty product list")
    void shouldHandleEmptyProductList() throws Exception {
        // Given
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(new ArrayList<>());
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.buildSummary(any(), any(), any(), anyBoolean())).thenReturn("No products matched");
        when(auditService.writeAuditLog(any(), any(), any(), anyString(), anyString())).thenReturn("audit_test.json");

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalRecords());
        assertEquals(0, result.getMatchedRecords());
        assertEquals(0, result.getChangedRecords());
        assertEquals(0, result.getSkippedRecords());

        verify(csvService, times(1)).writeCsvAtomic(eq(new ArrayList<>()), anyString());
    }

    @Test
    @DisplayName("Should handle plan without summary requirement")
    void shouldHandlePlanWithoutSummaryRequirement() throws Exception {
        // Given
        validPlan.setSummaryRequired(false);
        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(true);
        when(actionHandler.apply(any(), any(), anyBoolean())).thenReturn(mockChanges.get(0));
        when(actionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0));
        when(auditService.writeAuditLog(any(), any(), any(), anyString(), isNull())).thenReturn("audit_test.json");

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertNull(result.getSummary());

        verify(actionHandler, never()).buildSummary(any(), any(), any(), anyBoolean());
        verify(auditService, times(1)).writeAuditLog(any(), any(), any(), anyString(), isNull());
    }

    @Test
    @DisplayName("Should handle multiple action handlers")
    void shouldHandleMultipleActionHandlers() throws Exception {
        // Given
        ActionHandler anotherActionHandler = mock(ActionHandler.class);
        List<ActionHandler> actionHandlers = List.of(actionHandler, anotherActionHandler);
        ReflectionTestUtils.setField(executorService, "actionHandlers", actionHandlers);

        when(idempotencyService.alreadyExecuted("exec-test-001")).thenReturn(false);
        when(csvService.readCsv(anyString())).thenReturn(testRecords);
        when(actionHandler.supports(any())).thenReturn(false);
        when(anotherActionHandler.supports(any())).thenReturn(true);
        when(anotherActionHandler.apply(any(), any(), anyBoolean())).thenReturn(mockChanges.get(0));
        when(anotherActionHandler.buildSkippedEntry(any(), anyString())).thenReturn(mockSkipped.get(0));
        when(anotherActionHandler.buildSummary(any(), any(), any(), anyBoolean())).thenReturn("Summary");
        when(auditService.writeAuditLog(any(), any(), any(), anyString(), anyString())).thenReturn("audit_test.json");

        // When
        ExecutionResult result = executorService.execute(validPlan);

        // Then
        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());

        verify(actionHandler, times(1)).supports(any());
        verify(anotherActionHandler, times(1)).supports(any());
        verify(anotherActionHandler, times(1)).apply(any(), any(), anyBoolean());
    }
}
