package com.agent.controller;

import com.agent.model.ExecutionPlan;
import com.agent.model.ExecutionResult;
import com.agent.service.ExecutorService;
import com.agent.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for the Executor.
 *
 * Endpoints:
 *   POST /api/execute                     — Execute a plan JSON
 *   GET  /api/execute/{executionId}/status — Check idempotency status
 *   GET  /api/execute/history             — All past executions
 */
@Slf4j
@RestController
@RequestMapping("/api/execute")
public class ExecutorController {

    private final ExecutorService executorService;
    private final IdempotencyService idempotencyService;

    public ExecutorController(ExecutorService executorService,
                              IdempotencyService idempotencyService) {
        this.executorService     = executorService;
        this.idempotencyService  = idempotencyService;
        log.info("ExecutorController initialized");
    }

    /**
     * Execute a plan. Accepts the full ExecutionPlan JSON in the request body.
     * Safe to call multiple times — idempotent on execution_id.
     */
    @PostMapping
    @Operation(summary = "Execute a validated plan")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "PriceAdjustmentPlan",
                            value = """
{
  "executionId": "exec-20250228-002",
  "version": "1.0",
  "instruction": "Increase prices by 10% for all in-stock fitness products.",
  "createdAt": "2025-02-28T10:05:00",
  "filters": [
    { "field": "category", "operator": "EQUALS", "value": "fitness" },
    { "field": "in_stock", "operator": "EQUALS", "value": "true" }
  ],
  "action": {
    "type": "PRICE_ADJUSTMENT",
    "adjustmentMode": "PERCENTAGE",
    "value": 10.0,
    "direction": "INCREASE",
    "decimalPlaces": 2
  },
  "summaryRequired": true,
  "dryRun": false
}
"""
                    )
            )
    )
    public ResponseEntity<ExecutionResult> execute(@org.springframework.web.bind.annotation.RequestBody ExecutionPlan plan) {
        log.info("Received execution request for executionId: {}", plan.getExecutionId());
        log.debug("Execution plan details: version={}, instruction={}, filters={}, action={}, dryRun={}", 
                plan.getVersion(), plan.getInstruction(), plan.getFilters(), plan.getAction(), plan.isDryRun());
        
        try {
            ExecutionResult result = executorService.execute(plan);
            log.info("Execution completed successfully for executionId: {}, status: {}", 
                    plan.getExecutionId(), result.getStatus());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Execution failed for executionId: {}", plan.getExecutionId(), e);
            throw e;
        }
    }

    /**
     * Check whether a given execution_id has already been executed.
     */
    @GetMapping("/{executionId}/status")
    @Operation(summary = "Check idempotency status for an execution ID")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String executionId) {
        log.debug("Checking status for executionId: {}", executionId);
        boolean executed = idempotencyService.alreadyExecuted(executionId);
        log.info("Status check for executionId: {}, already_executed: {}", executionId, executed);
        
        return ResponseEntity.ok(Map.of(
                "execution_id", executionId,
                "already_executed", executed,
                "message", executed
                        ? "This execution_id has already been completed. Re-execution will be skipped."
                        : "This execution_id has not been executed yet."
        ));
    }

    /**
     * Returns all past execution records.
     */
    @GetMapping("/history")
    @Operation(summary = "Get all past execution records")
    public ResponseEntity<Map<String, Map<String, String>>> getHistory() {
        log.debug("Retrieving execution history");
        Map<String, Map<String, String>> history = idempotencyService.getAllExecutions();
        log.info("Retrieved execution history with {} records", history.size());
        return ResponseEntity.ok(history);
    }
}
