package com.agent.controller;

import com.agent.model.ExecutionPlan;
import com.agent.model.ExecutionResult;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import com.agent.service.ExecutorService;
import com.agent.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutorController.class)
@DisplayName("ExecutorController Tests")
class ExecutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutorService executorService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Autowired
    private ObjectMapper objectMapper;

    private ExecutionPlan validPlan;
    private ExecutionResult successfulResult;
    private ExecutionResult skippedResult;
    private String planJson;

    @BeforeEach
    void setUp() {
        // Create a valid execution plan for testing
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

        // Create successful execution result
        successfulResult = new ExecutionResult();
        successfulResult.setExecutionId("exec-test-001");
        successfulResult.setStatus("COMPLETED");
        successfulResult.setMessage("Execution completed successfully.");
        successfulResult.setExecutedAt(LocalDateTime.now());
        successfulResult.setTotalRecords(6);
        successfulResult.setMatchedRecords(2);
        successfulResult.setChangedRecords(2);
        successfulResult.setSkippedRecords(4);
        successfulResult.setSummary("2 product(s) updated. 4 product(s) skipped.");

        // Create skipped execution result
        skippedResult = ExecutionResult.skipped("exec-test-001");

        try {
            planJson = objectMapper.writeValueAsString(validPlan);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize test plan", e);
        }
    }

    @Test
    @DisplayName("Should execute plan successfully")
    void shouldExecutePlanSuccessfully() throws Exception {
        // Given
        when(executorService.execute(any(ExecutionPlan.class))).thenReturn(successfulResult);

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("exec-test-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Execution completed successfully."))
                .andExpect(jsonPath("$.totalRecords").value(6))
                .andExpect(jsonPath("$.matchedRecords").value(2))
                .andExpect(jsonPath("$.changedRecords").value(2))
                .andExpect(jsonPath("$.skippedRecords").value(4));

        verify(executorService, times(1)).execute(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should skip execution when already executed")
    void shouldSkipExecutionWhenAlreadyExecuted() throws Exception {
        // Given
        when(executorService.execute(any(ExecutionPlan.class))).thenReturn(skippedResult);

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("exec-test-001"))
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.message").value("This execution_id has already been completed."));

        verify(executorService, times(1)).execute(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle execution failure")
    void shouldHandleExecutionFailure() throws Exception {
        // Given
        when(executorService.execute(any(ExecutionPlan.class)))
                .thenThrow(new com.agent.exception.AgentException("EXECUTION_ERROR", "Execution failed"));

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error_code").value("EXECUTION_ERROR"))
                .andExpect(jsonPath("$.message").value("Execution failed"));

        verify(executorService, times(1)).execute(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle malformed JSON request")
    void shouldHandleMalformedJsonRequest() throws Exception {
        // Given
        String malformedJson = "{ invalid json }";

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verify(executorService, never()).execute(any());
    }

    @Test
    @DisplayName("Should check execution status - not executed")
    void shouldCheckExecutionStatusNotExecuted() throws Exception {
        // Given
        String executionId = "exec-test-001";
        when(idempotencyService.alreadyExecuted(executionId)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/execute/{executionId}/status", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").value(executionId))
                .andExpect(jsonPath("$.already_executed").value(false))
                .andExpect(jsonPath("$.message").value("This execution_id has not been executed yet."));

        verify(idempotencyService, times(1)).alreadyExecuted(executionId);
    }

    @Test
    @DisplayName("Should check execution status - already executed")
    void shouldCheckExecutionStatusAlreadyExecuted() throws Exception {
        // Given
        String executionId = "exec-test-001";
        when(idempotencyService.alreadyExecuted(executionId)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/execute/{executionId}/status", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").value(executionId))
                .andExpect(jsonPath("$.already_executed").value(true))
                .andExpect(jsonPath("$.message").value("This execution_id has already been completed. Re-execution will be skipped."));

        verify(idempotencyService, times(1)).alreadyExecuted(executionId);
    }

    @Test
    @DisplayName("Should get execution history")
    void shouldGetExecutionHistory() throws Exception {
        // Given
        Map<String, Map<String, String>> history = Map.of(
                "exec-test-001", Map.of("status", "COMPLETED", "executed_at", "2025-02-28T10:00:00"),
                "exec-test-002", Map.of("status", "COMPLETED", "executed_at", "2025-02-28T11:00:00")
        );
        when(idempotencyService.getAllExecutions()).thenReturn(history);

        // When & Then
        mockMvc.perform(get("/api/execute/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exec-test-001.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exec-test-001.executed_at").value("2025-02-28T10:00:00"))
                .andExpect(jsonPath("$.exec-test-002.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exec-test-002.executed_at").value("2025-02-28T11:00:00"));

        verify(idempotencyService, times(1)).getAllExecutions();
    }

    @Test
    @DisplayName("Should get empty execution history")
    void shouldGetEmptyExecutionHistory() throws Exception {
        // Given
        when(idempotencyService.getAllExecutions()).thenReturn(Map.of());

        // When & Then
        mockMvc.perform(get("/api/execute/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(idempotencyService, times(1)).getAllExecutions();
    }

    @Test
    @DisplayName("Should handle dry run execution")
    void shouldHandleDryRunExecution() throws Exception {
        // Given
        validPlan.setDryRun(true);
        ExecutionResult dryRunResult = new ExecutionResult();
        dryRunResult.setExecutionId("exec-test-001");
        dryRunResult.setStatus("DRY_RUN");
        dryRunResult.setMessage("Dry run completed. No data was modified.");
        dryRunResult.setExecutedAt(LocalDateTime.now());
        dryRunResult.setTotalRecords(6);
        dryRunResult.setMatchedRecords(2);
        dryRunResult.setChangedRecords(2);
        dryRunResult.setSkippedRecords(4);

        String dryRunPlanJson = objectMapper.writeValueAsString(validPlan);
        when(executorService.execute(any(ExecutionPlan.class))).thenReturn(dryRunResult);

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunPlanJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRY_RUN"))
                .andExpect(jsonPath("$.message").value("Dry run completed. No data was modified."));

        verify(executorService, times(1)).execute(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle null request body")
    void shouldHandleNullRequestBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(executorService, never()).execute(any());
    }

    @Test
    @DisplayName("Should validate plan with missing required fields")
    void shouldValidatePlanWithMissingRequiredFields() throws Exception {
        // Given
        ExecutionPlan incompletePlan = new ExecutionPlan();
        incompletePlan.setExecutionId("exec-test-002");
        // Missing other required fields
        
        String incompleteJson = "{\"executionId\":\"exec-test-002\"}";
        when(executorService.execute(any(ExecutionPlan.class)))
                .thenThrow(new com.agent.exception.AgentException("VALIDATION_ERROR", "Missing required fields"));

        // When & Then
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

        verify(executorService, times(1)).execute(any(ExecutionPlan.class));
    }
}
