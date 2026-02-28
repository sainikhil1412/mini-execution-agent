package com.agent.controller;

import com.agent.model.ExecutionPlan;
import com.agent.service.PlanGenerationService;
import com.agent.service.PlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST Controller for the Planner.
 *
 * Endpoints:
 *   POST /api/plan/validate  — Parse + validate a raw plan JSON string
 *
 * In a production system this controller would also expose:
 *   POST /api/plan/generate  — Accept natural-language instruction, call LLM, return plan
 *
 * For this assignment: generate the plan manually via ChatGPT,
 * then POST the JSON here to validate it before sending to /api/execute.
 */
@Slf4j
@RestController
@RequestMapping("/api/plan")
public class PlannerController {

    private final PlannerService plannerService;
    private final PlanGenerationService planGenerationService;

    public PlannerController(PlannerService plannerService,
                             PlanGenerationService planGenerationService) {
        this.plannerService = plannerService;
        this.planGenerationService = planGenerationService;
        log.info("PlannerController initialized");
    }

    /**
     * Validate a raw plan JSON string.
     * Returns the parsed plan if valid, or error details if invalid.
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate a raw plan JSON string")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "PriceAdjustmentPlan",
                            value = """
{
  "executionId": "exec-20250228-001",
  "version": "1.0",
  "instruction": "Increase prices by 10% for all in-stock fitness products.",
  "createdAt": "2025-02-28T10:00:00",
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
    public ResponseEntity<Map<String, Object>> validatePlan(@org.springframework.web.bind.annotation.RequestBody ExecutionPlan plan) {
        log.info("Received plan validation request for executionId: {}", plan.getExecutionId());
        log.debug("Plan details: version={}, instruction={}, filters={}, action={}", 
                plan.getVersion(), plan.getInstruction(), plan.getFilters(), plan.getAction());
        
        try {
            ExecutionPlan validated = plannerService.validatePlan(plan);
            log.info("Plan validation successful for executionId: {}", plan.getExecutionId());
            return ResponseEntity.ok(Map.of(
                    "status", "VALID",
                    "message", "Plan is valid and ready for execution.",
                    "plan", validated
            ));
        } catch (Exception e) {
            log.error("Plan validation failed for executionId: {}", plan.getExecutionId(), e);
            throw e;
        }
    }

    /**
     * Accept a natural-language instruction, "call" an LLM to generate plan JSON,
     * then validate that JSON using the same /validate logic.
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate a plan from natural-language instruction (mock LLM)")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "text/plain",
                    examples = @ExampleObject(
                            name = "Instruction",
                            value = "Increase prices by 10% for all in-stock fitness products. Generate a summary."
                    )
            )
    )
    public ResponseEntity<Map<String, Object>> generatePlan(@org.springframework.web.bind.annotation.RequestBody String instruction) {
        log.info("Received plan generation request for instruction: {}", instruction);
        
        try {
            PlanGenerationService.PlanGenerationResult generated = planGenerationService.generate(instruction);
            log.debug("Generated plan raw JSON: {}", generated.rawJson());

            ExecutionPlan validatedPlan = plannerService.parsePlanAndValidate(generated.rawJson());
            log.info("Plan generation and validation successful for executionId: {}", validatedPlan.getExecutionId());
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("prompt", generated.prompt());
            response.put("rawJson", generated.rawJson());
            response.put("status", "VALID");
            response.put("message", "Plan is valid and ready for execution.");
            response.put("plan", validatedPlan);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Plan generation failed for instruction: {}", instruction, e);
            throw e;
        }
    }
}
