package com.agent.controller;

import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import com.agent.service.PlanGenerationService;
import com.agent.service.PlannerService;
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

@WebMvcTest(PlannerController.class)
@DisplayName("PlannerController Tests")
class PlannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlannerService plannerService;

    @MockBean
    private PlanGenerationService planGenerationService;

    @Autowired
    private ObjectMapper objectMapper;

    private ExecutionPlan validPlan;
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

        try {
            planJson = objectMapper.writeValueAsString(validPlan);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize test plan", e);
        }
    }

    @Test
    @DisplayName("Should validate plan successfully")
    void shouldValidatePlanSuccessfully() throws Exception {
        // Given
        when(plannerService.validatePlan(any(ExecutionPlan.class))).thenReturn(validPlan);

        // When & Then
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.message").value("Plan is valid and ready for execution."))
                .andExpect(jsonPath("$.plan.executionId").value("exec-test-001"))
                .andExpect(jsonPath("$.plan.version").value("1.0"));

        verify(plannerService, times(1)).validatePlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should return validation error when plan is invalid")
    void shouldReturnValidationErrorWhenPlanIsInvalid() throws Exception {
        // Given
        when(plannerService.validatePlan(any(ExecutionPlan.class)))
                .thenThrow(new com.agent.exception.AgentException("VALIDATION_ERROR", "Invalid plan"));

        // When & Then
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid plan"));

        verify(plannerService, times(1)).validatePlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle malformed JSON request")
    void shouldHandleMalformedJsonRequest() throws Exception {
        // Given
        String malformedJson = "{ invalid json }";

        // When & Then
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verify(plannerService, never()).validatePlan(any());
    }

    @Test
    @DisplayName("Should generate plan successfully")
    void shouldGeneratePlanSuccessfully() throws Exception {
        // Given
        String instruction = "Increase prices by 10% for fitness products";
        PlanGenerationService.PlanGenerationResult result = 
                new PlanGenerationService.PlanGenerationResult("prompt", planJson);
        
        when(planGenerationService.generate(instruction)).thenReturn(result);
        when(plannerService.parsePlanAndValidate(planJson)).thenReturn(validPlan);

        // When & Then
        mockMvc.perform(post("/api/plan/generate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(instruction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value("prompt"))
                .andExpect(jsonPath("$.rawJson").value(planJson))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.plan.executionId").value("exec-test-001"));

        verify(planGenerationService, times(1)).generate(instruction);
        verify(plannerService, times(1)).parsePlanAndValidate(planJson);
    }

    @Test
    @DisplayName("Should handle plan generation failure")
    void shouldHandlePlanGenerationFailure() throws Exception {
        // Given
        String instruction = "Invalid instruction";
        when(planGenerationService.generate(instruction))
                .thenThrow(new com.agent.exception.AgentException("GENERATION_ERROR", "Failed to generate plan"));

        // When & Then
        mockMvc.perform(post("/api/plan/generate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(instruction))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error_code").value("GENERATION_ERROR"));

        verify(planGenerationService, times(1)).generate(instruction);
        verify(plannerService, never()).parsePlanAndValidate(any());
    }

    @Test
    @DisplayName("Should handle empty instruction for plan generation")
    void shouldHandleEmptyInstructionForPlanGeneration() throws Exception {
        // Given
        String emptyInstruction = "";

        // When & Then
        mockMvc.perform(post("/api/plan/generate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(emptyInstruction))
                .andExpect(status().isBadRequest());

        verify(planGenerationService, never()).generate(any());
        verify(plannerService, never()).parsePlanAndValidate(any());
    }

    @Test
    @DisplayName("Should validate plan with missing required fields")
    void shouldValidatePlanWithMissingRequiredFields() throws Exception {
        // Given
        ExecutionPlan incompletePlan = new ExecutionPlan();
        incompletePlan.setExecutionId("exec-test-002");
        // Missing other required fields
        
        String incompleteJson = "{\"executionId\":\"exec-test-002\"}";
        when(plannerService.validatePlan(any(ExecutionPlan.class)))
                .thenThrow(new com.agent.exception.AgentException("VALIDATION_ERROR", "Missing required fields"));

        // When & Then
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

        verify(plannerService, times(1)).validatePlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle null request body")
    void shouldHandleNullRequestBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(plannerService, never()).validatePlan(any());
    }
}
