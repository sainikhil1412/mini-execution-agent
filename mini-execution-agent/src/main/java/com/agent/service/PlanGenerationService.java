package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.llm.PlannerPromptTemplates;
import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PlanGenerationService {

    private final ObjectMapper mapper;
    private final PlannerService plannerService;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 200L;

    public PlanGenerationService(PlannerService plannerService) {
        this.plannerService = plannerService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        log.info("PlanGenerationService initialized with ObjectMapper");
    }

    public PlanGenerationResult generate(String instruction) {
        log.info("Starting plan generation for instruction: {}", instruction);
        String lastPrompt = null;
        String lastRawJson = null;
        String lastError = "";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            log.debug("Plan generation attempt {} of {}", attempt, MAX_ATTEMPTS);
            lastPrompt = buildPrompt(instruction, lastError);
            lastRawJson = mockLlmGeneratePlanJson(lastPrompt, instruction);

            try {
                plannerService.parsePlanAndValidate(lastRawJson);
                log.info("Plan generation successful on attempt {} for instruction: {}", attempt, instruction);
                return new PlanGenerationResult(lastPrompt, lastRawJson);
            } catch (AgentException e) {
                lastError = e.getMessage();
                log.warn("Plan generation attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                } else {
                    log.error("Plan generation failed after {} attempts for instruction: {}", MAX_ATTEMPTS, instruction);
                    throw e;
                }
            }
        }

        throw new AgentException("LLM_MOCK_ERROR", "Failed to generate a valid plan after retries.");
    }

    private String mockLlmGeneratePlanJson(String prompt, String instruction) {
        log.debug("Generating mock plan from instruction: {}", instruction);
        ExecutionPlan plan = buildMockPlanFromInstruction(instruction);
        try {
            String json = mapper.writeValueAsString(plan);
            log.debug("Generated mock plan JSON for executionId: {}", plan.getExecutionId());
            return json;
        } catch (Exception e) {
            log.error("Failed to build mock plan JSON: {}", e.getMessage(), e);
            throw new AgentException("LLM_MOCK_ERROR", "Failed to build mock plan JSON: " + e.getMessage());
        }
    }

    private String buildPrompt(String instruction, String validationError) {
        String error = validationError == null || validationError.isBlank()
                ? "(none)"
                : validationError;
        return String.format(PlannerPromptTemplates.PLAN_PROMPT_TEMPLATE, instruction, error);
    }

    private void backoff(int attempt) {
        long delay = BASE_BACKOFF_MS * (1L << (attempt - 1));
        log.debug("Backing off for {} ms on attempt {}", delay, attempt);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            log.warn("Backoff interrupted on attempt {}", attempt);
            Thread.currentThread().interrupt();
        }
    }

    private ExecutionPlan buildMockPlanFromInstruction(String instruction) {
        log.debug("Building mock plan from instruction: {}", instruction);
        ExecutionPlan plan = new ExecutionPlan();
        plan.setExecutionId("exec-" + UUID.randomUUID());
        plan.setVersion("1.0");
        plan.setInstruction(instruction);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setSummaryRequired(true);
        plan.setDryRun(false);

        plan.setFilters(buildFilters(instruction));
        plan.setAction(buildPriceAdjustmentAction(instruction));
        log.debug("Built mock plan with {} filters for executionId: {}", plan.getFilters().size(), plan.getExecutionId());
        return plan;
    }

    private PriceAdjustmentAction buildPriceAdjustmentAction(String instruction) {
        String lower = instruction == null ? "" : instruction.toLowerCase();

        PriceAdjustmentAction action = new PriceAdjustmentAction();
        action.setAdjustmentMode(
                (lower.contains("%") || lower.contains("percent") || lower.contains("percentage"))
                        ? PriceAdjustmentAction.AdjustmentMode.PERCENTAGE
                        : PriceAdjustmentAction.AdjustmentMode.FLAT
        );
        action.setDirection(
                (lower.contains("decrease") || lower.contains("reduce") || lower.contains("lower"))
                        ? PriceAdjustmentAction.Direction.DECREASE
                        : PriceAdjustmentAction.Direction.INCREASE
        );
        action.setDecimalPlaces(2);
        action.setValue(extractNumericValue(lower, action.getAdjustmentMode()));
        return action;
    }

    private double extractNumericValue(String lower, PriceAdjustmentAction.AdjustmentMode mode) {
        double value = 10.0;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(lower);
        if (matcher.find()) {
            value = Double.parseDouble(matcher.group(1));
        }
        if (mode == PriceAdjustmentAction.AdjustmentMode.PERCENTAGE && value > 100.0) {
            value = 100.0;
        }
        if (value <= 0.0) {
            value = 10.0;
        }
        return value;
    }

    private List<Filter> buildFilters(String instruction) {
        String lower = instruction == null ? "" : instruction.toLowerCase();
        List<Filter> filters = new ArrayList<>();

        if (lower.contains("fitness")) {
            filters.add(new Filter("category", Filter.FilterOperator.EQUALS, "fitness"));
        }
        if (lower.contains("in stock") || lower.contains("in-stock")) {
            filters.add(new Filter("in_stock", Filter.FilterOperator.EQUALS, "true"));
        }
        if (lower.contains("out of stock") || lower.contains("out-of-stock")) {
            filters.add(new Filter("in_stock", Filter.FilterOperator.EQUALS, "false"));
        }

        if (filters.isEmpty()) {
            filters.add(new Filter("category", Filter.FilterOperator.EQUALS, "general"));
            log.debug("No specific filters found in instruction, using default filter");
        } else {
            log.debug("Extracted {} filters from instruction", filters.size());
        }
        return filters;
    }

    public record PlanGenerationResult(String prompt, String rawJson) {
    }
}
