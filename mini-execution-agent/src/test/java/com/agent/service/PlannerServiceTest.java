package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import com.agent.validator.PlanValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlannerService Tests")
class PlannerServiceTest {

    @Mock
    private PlanValidator validator;

    @InjectMocks
    private PlannerService plannerService;

    private ObjectMapper objectMapper;
    private ExecutionPlan validPlan;
    private String validPlanJson;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

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
            validPlanJson = objectMapper.writeValueAsString(validPlan);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize test plan", e);
        }
    }

    @Test
    @DisplayName("Should parse and validate valid plan JSON")
    void shouldParseAndValidateValidPlanJson() throws Exception {
        // Given
        doNothing().when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When
        ExecutionPlan result = plannerService.parsePlanAndValidate(validPlanJson);

        // Then
        assertNotNull(result);
        assertEquals("exec-test-001", result.getExecutionId());
        assertEquals("1.0", result.getVersion());
        assertEquals("Increase prices by 10% for all in-stock fitness products", result.getInstruction());
        assertEquals(2, result.getFilters().size());
        assertNotNull(result.getAction());
        assertTrue(result.isSummaryRequired());
        assertFalse(result.isDryRun());

        verify(validator, times(1)).validateExecutionPlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should validate already parsed plan")
    void shouldValidateAlreadyParsedPlan() throws Exception {
        // Given
        doNothing().when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When
        ExecutionPlan result = plannerService.validatePlan(validPlan);

        // Then
        assertNotNull(result);
        assertEquals(validPlan, result);

        verify(validator, times(1)).validateExecutionPlan(validPlan);
    }

    @Test
    @DisplayName("Should throw exception for null JSON string")
    void shouldThrowExceptionForNullJsonString() {
        // Given
        String nullJson = null;

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(nullJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertEquals("Plan JSON body is required.", exception.getMessage());

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should throw exception for blank JSON string")
    void shouldThrowExceptionForBlankJsonString() {
        // Given
        String blankJson = "   ";

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(blankJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertEquals("Plan JSON body is required.", exception.getMessage());

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should throw exception for malformed JSON")
    void shouldThrowExceptionForMalformedJson() {
        // Given
        String malformedJson = "{ invalid json }";

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(malformedJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to parse plan JSON"));

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should throw exception when validation fails")
    void shouldThrowExceptionWhenValidationFails() throws Exception {
        // Given
        doThrow(new AgentException("VALIDATION_ERROR", "Invalid plan"))
                .when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(validPlanJson);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals("Invalid plan", exception.getMessage());

        verify(validator, times(1)).validateExecutionPlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should throw exception when validation fails for already parsed plan")
    void shouldThrowExceptionWhenValidationFailsForAlreadyParsedPlan() {
        // Given
        doThrow(new AgentException("VALIDATION_ERROR", "Invalid plan"))
                .when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.validatePlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals("Invalid plan", exception.getMessage());

        verify(validator, times(1)).validateExecutionPlan(validPlan);
    }

    @Test
    @DisplayName("Should handle JSON with missing required fields")
    void shouldHandleJsonWithMissingRequiredFields() {
        // Given
        String incompleteJson = "{\"executionId\":\"exec-test-001\"}";

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(incompleteJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to parse plan JSON"));

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should handle JSON with invalid enum values")
    void shouldHandleJsonWithInvalidEnumValues() {
        // Given
        String invalidEnumJson = """
            {
              "executionId": "exec-test-001",
              "version": "1.0",
              "instruction": "Test instruction",
              "createdAt": "2025-02-28T10:00:00",
              "filters": [
                {
                  "field": "category",
                  "operator": "INVALID_OPERATOR",
                  "value": "fitness"
                }
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
            """;

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(invalidEnumJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to parse plan JSON"));

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should handle JSON with invalid date format")
    void shouldHandleJsonWithInvalidDateFormat() {
        // Given
        String invalidDateJson = """
            {
              "executionId": "exec-test-001",
              "version": "1.0",
              "instruction": "Test instruction",
              "createdAt": "invalid-date",
              "filters": [],
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
            """;

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            plannerService.parsePlanAndValidate(invalidDateJson);
        });

        assertEquals("PARSE_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to parse plan JSON"));

        verify(validator, never()).validateExecutionPlan(any());
    }

    @Test
    @DisplayName("Should delegate to parsePlanAndValidate when calling parsePlan")
    void shouldDelegateToParsePlanAndValidateWhenCallingParsePlan() throws Exception {
        // Given
        PlannerService spyPlannerService = spy(plannerService);
        doNothing().when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When
        ExecutionPlan result = spyPlannerService.parsePlan(validPlanJson);

        // Then
        assertNotNull(result);
        verify(spyPlannerService, times(1)).parsePlanAndValidate(validPlanJson);
        verify(validator, times(1)).validateExecutionPlan(any(ExecutionPlan.class));
    }

    @Test
    @DisplayName("Should handle JSON with extra fields")
    void shouldHandleJsonWithExtraFields() throws Exception {
        // Given
        String jsonWithExtraFields = validPlanJson.replace("}", ", \"extraField\": \"extraValue\"}");
        doNothing().when(validator).validateExecutionPlan(any(ExecutionPlan.class));

        // When
        ExecutionPlan result = plannerService.parsePlanAndValidate(jsonWithExtraFields);

        // Then
        assertNotNull(result);
        assertEquals("exec-test-001", result.getExecutionId());

        verify(validator, times(1)).validateExecutionPlan(any(ExecutionPlan.class));
    }
}
