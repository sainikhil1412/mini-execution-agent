package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.ExecutionPlan;
import com.agent.validator.PlanValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Planner Service.
 *
 * In this implementation, the plan is generated MANUALLY via ChatGPT
 * and pasted into the POST /api/plan/validate endpoint.
 *
 * In a production system, this service would:
 * 1. Send the natural-language instruction to an LLM API with a strict system prompt
 * 2. Parse the LLM response as JSON
 * 3. Validate and return the plan
 *
 * The executor is completely decoupled from this — adding LLM integration
 * in the future requires changing ONLY this service.
 */
@Slf4j
@Service
public class PlannerService {

    private final ObjectMapper mapper;
    private final PlanValidator validator;

    public PlannerService(PlanValidator validator) {
        this.validator = validator;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        log.info("PlannerService initialized with ObjectMapper configured");
    }

    /**
     * Parses a raw JSON string into an ExecutionPlan and validates it.
     * Throws AgentException if JSON is malformed or plan is invalid.
     */
    public ExecutionPlan parsePlan(String planJson) {
        log.debug("Parsing plan JSON string");
        return parsePlanAndValidate(planJson);
    }

    /**
     * Parses a raw JSON string into an ExecutionPlan and validates it.
     * Throws AgentException if JSON is malformed or plan is invalid.
     */
    public ExecutionPlan parsePlanAndValidate(String planJson) {
        log.debug("Parsing and validating plan JSON");
        if (planJson == null || planJson.isBlank()) {
            log.warn("Plan JSON body is null or blank");
            throw new AgentException("PARSE_ERROR", "Plan JSON body is required.");
        }
        ExecutionPlan plan;
        try {
            plan = mapper.readValue(planJson, ExecutionPlan.class);
            log.debug("Successfully parsed plan JSON for executionId: {}", plan.getExecutionId());
        } catch (Exception e) {
            log.error("Failed to parse plan JSON: {}", e.getMessage(), e);
            throw new AgentException("PARSE_ERROR",
                    "Failed to parse plan JSON: " + e.getMessage());
        }
        validator.validateExecutionPlan(plan);
        log.info("Plan parsed and validated successfully for executionId: {}", plan.getExecutionId());
        return plan;
    }

    /**
     * Validates an already parsed ExecutionPlan.
     * Throws AgentException if plan is invalid.
     */
    public ExecutionPlan validatePlan(ExecutionPlan plan) {
        log.debug("Validating execution plan for executionId: {}", plan.getExecutionId());
        try {
            validator.validateExecutionPlan(plan);
            log.info("Plan validation successful for executionId: {}", plan.getExecutionId());
            return plan;
        } catch (Exception e) {
            log.error("Plan validation failed for executionId: {}", plan.getExecutionId(), e);
            throw e;
        }
    }


}
