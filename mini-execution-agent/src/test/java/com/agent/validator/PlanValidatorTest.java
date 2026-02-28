package com.agent.validator;

import com.agent.exception.AgentException;
import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import com.agent.model.PriceAdjustmentAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanValidator Tests")
class PlanValidatorTest {

    @Mock
    private ActionValidator actionValidator;

    private PlanValidator planValidator;

    private ExecutionPlan validPlan;

    @BeforeEach
    void setUp() {
        List<ActionValidator> actionValidators = List.of(actionValidator);
        planValidator = new PlanValidator(actionValidators);

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
    }

    @Test
    @DisplayName("Should validate valid plan successfully")
    void shouldValidateValidPlanSuccessfully() {
        // Given
        when(actionValidator.supports(any())).thenReturn(true);

        // When & Then
        assertDoesNotThrow(() -> planValidator.validateExecutionPlan(validPlan));

        verify(actionValidator, times(1)).supports(any());
        verify(actionValidator, times(1)).validate(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for null execution ID")
    void shouldThrowExceptionForNullExecutionId() {
        // Given
        validPlan.setExecutionId(null);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("execution_id is required"));
    }

    @Test
    @DisplayName("Should throw exception for blank execution ID")
    void shouldThrowExceptionForBlankExecutionId() {
        // Given
        validPlan.setExecutionId("   ");

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("execution_id is required"));
    }

    @Test
    @DisplayName("Should throw exception for null version")
    void shouldThrowExceptionForNullVersion() {
        // Given
        validPlan.setVersion(null);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("version is required"));
    }

    @Test
    @DisplayName("Should throw exception for unsupported version")
    void shouldThrowExceptionForUnsupportedVersion() {
        // Given
        validPlan.setVersion("2.0");

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("version is required and must be one of: [1.0]"));
    }

    @Test
    @DisplayName("Should throw exception for null filters")
    void shouldThrowExceptionForNullFilters() {
        // Given
        validPlan.setFilters(null);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("filters must not be empty"));
    }

    @Test
    @DisplayName("Should throw exception for empty filters")
    void shouldThrowExceptionForEmptyFilters() {
        // Given
        validPlan.setFilters(new ArrayList<>());

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("filters must not be empty"));
    }

    @Test
    @DisplayName("Should throw exception for filter with null field")
    void shouldThrowExceptionForFilterWithNullField() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField(null);
        invalidFilter.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter.setValue("fitness");
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: field is required"));
    }

    @Test
    @DisplayName("Should throw exception for filter with blank field")
    void shouldThrowExceptionForFilterWithBlankField() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField("   ");
        invalidFilter.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter.setValue("fitness");
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: field is required"));
    }

    @Test
    @DisplayName("Should throw exception for filter with invalid field")
    void shouldThrowExceptionForFilterWithInvalidField() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField("invalid_field");
        invalidFilter.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter.setValue("fitness");
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: field 'invalid_field' is not a recognized column"));
    }

    @Test
    @DisplayName("Should throw exception for filter with null operator")
    void shouldThrowExceptionForFilterWithNullOperator() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField("category");
        invalidFilter.setOperator(null);
        invalidFilter.setValue("fitness");
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: operator is required"));
    }

    @Test
    @DisplayName("Should throw exception for filter with null value")
    void shouldThrowExceptionForFilterWithNullValue() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField("category");
        invalidFilter.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter.setValue(null);
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: value is required"));
    }

    @Test
    @DisplayName("Should throw exception for filter with blank value")
    void shouldThrowExceptionForFilterWithBlankValue() {
        // Given
        Filter invalidFilter = new Filter();
        invalidFilter.setField("category");
        invalidFilter.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter.setValue("   ");
        validPlan.setFilters(List.of(invalidFilter));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Filter[0]: value is required"));
    }

    @Test
    @DisplayName("Should throw exception for null action")
    void shouldThrowExceptionForNullAction() {
        // Given
        validPlan.setAction(null);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("action is required"));
    }

    @Test
    @DisplayName("Should throw exception when no action validator found")
    void shouldThrowExceptionWhenNoActionValidatorFound() {
        // Given
        when(actionValidator.supports(any())).thenReturn(false);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("action.type is not supported: PRICE_ADJUSTMENT"));

        verify(actionValidator, times(1)).supports(any());
        verify(actionValidator, never()).validate(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when action validation fails")
    void shouldThrowExceptionWhenActionValidationFails() {
        // Given
        when(actionValidator.supports(any())).thenReturn(true);
        doThrow(new AgentException("ACTION_VALIDATION_ERROR", "Invalid action"))
                .when(actionValidator).validate(any(), any());

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid action"));

        verify(actionValidator, times(1)).supports(any());
        verify(actionValidator, times(1)).validate(any(), any());
    }

    @Test
    @DisplayName("Should handle multiple validation errors")
    void shouldHandleMultipleValidationErrors() {
        // Given
        validPlan.setExecutionId(null);
        validPlan.setVersion(null);
        validPlan.setFilters(new ArrayList<>());
        validPlan.setAction(null);

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        String message = exception.getMessage();
        assertTrue(message.contains("4 error(s)"));
        assertTrue(message.contains("execution_id is required"));
        assertTrue(message.contains("version is required"));
        assertTrue(message.contains("filters must not be empty"));
        assertTrue(message.contains("action is required"));
    }

    @Test
    @DisplayName("Should validate filter with case-insensitive field names")
    void shouldValidateFilterWithCaseInsensitiveFieldNames() {
        // Given
        Filter filterWithUpperCase = new Filter();
        filterWithUpperCase.setField("CATEGORY");
        filterWithUpperCase.setOperator(Filter.FilterOperator.EQUALS);
        filterWithUpperCase.setValue("fitness");
        validPlan.setFilters(List.of(filterWithUpperCase));
        when(actionValidator.supports(any())).thenReturn(true);

        // When & Then
        assertDoesNotThrow(() -> planValidator.validateExecutionPlan(validPlan));

        verify(actionValidator, times(1)).supports(any());
        verify(actionValidator, times(1)).validate(any(), any());
    }

    @Test
    @DisplayName("Should handle multiple filters with validation errors")
    void shouldHandleMultipleFiltersWithValidationErrors() {
        // Given
        Filter invalidFilter1 = new Filter();
        invalidFilter1.setField(null);
        invalidFilter1.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter1.setValue("fitness");

        Filter invalidFilter2 = new Filter();
        invalidFilter2.setField("invalid_field");
        invalidFilter2.setOperator(Filter.FilterOperator.EQUALS);
        invalidFilter2.setValue("true");

        validPlan.setFilters(List.of(invalidFilter1, invalidFilter2));

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            planValidator.validateExecutionPlan(validPlan);
        });

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        String message = exception.getMessage();
        assertTrue(message.contains("Filter[0]: field is required"));
        assertTrue(message.contains("Filter[1]: field 'invalid_field' is not a recognized column"));
    }

    @Test
    @DisplayName("Should validate all supported filter fields")
    void shouldValidateAllSupportedFilterFields() {
        // Given
        List<Filter> validFilters = List.of(
                new Filter("sku", Filter.FilterOperator.EQUALS, "TEST001"),
                new Filter("category", Filter.FilterOperator.EQUALS, "fitness"),
                new Filter("price", Filter.FilterOperator.GT, "10.0"),
                new Filter("in_stock", Filter.FilterOperator.EQUALS, "true")
        );
        validPlan.setFilters(validFilters);
        when(actionValidator.supports(any())).thenReturn(true);

        // When & Then
        assertDoesNotThrow(() -> planValidator.validateExecutionPlan(validPlan));

        verify(actionValidator, times(1)).supports(any());
        verify(actionValidator, times(1)).validate(any(), any());
    }
}
