package com.agent.validator;

import com.agent.exception.AgentException;
import com.agent.model.ExecutionPlan;
import com.agent.model.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PlanValidator {

    private static final List<String> SUPPORTED_VERSIONS = List.of("1.0");
    private static final List<String> ALLOWED_FIELDS = List.of("sku", "category", "price", "in_stock");

    private final List<ActionValidator> actionValidators;

    public PlanValidator(List<ActionValidator> actionValidators) {
        this.actionValidators = actionValidators;
        log.info("PlanValidator initialized with {} action validators", actionValidators.size());
    }

    public void validateExecutionPlan(ExecutionPlan plan) {
        log.debug("Starting validation for execution plan with executionId: {}", plan.getExecutionId());
        List<String> errors = new ArrayList<>();

        // execution_id
        if (plan.getExecutionId() == null || plan.getExecutionId().isBlank()) {
            errors.add("execution_id is required and must not be blank.");
            log.warn("Validation failed: execution_id is null or blank");
        }

        // version
        if (plan.getVersion() == null || !SUPPORTED_VERSIONS.contains(plan.getVersion())) {
            errors.add("version is required and must be one of: " + SUPPORTED_VERSIONS);
            log.warn("Validation failed: version '{}' is not supported. Supported versions: {}", plan.getVersion(), SUPPORTED_VERSIONS);
        }

        // filters
        if (plan.getFilters() == null || plan.getFilters().isEmpty()) {
            errors.add("filters must not be empty. At least one filter is required.");
            log.warn("Validation failed: filters list is null or empty");
        } else {
            log.debug("Validating {} filters", plan.getFilters().size());
            for (int i = 0; i < plan.getFilters().size(); i++) {
                Filter f = plan.getFilters().get(i);
                if (f.getField() == null || f.getField().isBlank()) {
                    errors.add("Filter[" + i + "]: field is required.");
                    log.warn("Validation failed: filter[{}] has null or blank field", i);
                } else if (!ALLOWED_FIELDS.contains(f.getField().toLowerCase())) {
                    errors.add("Filter[" + i + "]: field '" + f.getField() + "' is not a recognized column. Allowed: " + ALLOWED_FIELDS);
                    log.warn("Validation failed: filter[{}] field '{}' is not allowed. Allowed fields: {}", i, f.getField(), ALLOWED_FIELDS);
                }
                if (f.getOperator() == null) {
                    errors.add("Filter[" + i + "]: operator is required.");
                    log.warn("Validation failed: filter[{}] has null operator", i);
                }
                if (f.getValue() == null || f.getValue().isBlank()) {
                    errors.add("Filter[" + i + "]: value is required.");
                    log.warn("Validation failed: filter[{}] has null or blank value", i);
                }
            }
        }

        // action
        if (plan.getAction() == null) {
            errors.add("action is required.");
            log.warn("Validation failed: action is null");
        } else {
            log.debug("Validating action of type: {}", plan.getAction().getType());
            Optional<ActionValidator> actionValidator = actionValidators.stream()
                    .filter(v -> v.supports(plan.getAction()))
                    .findFirst();

            if (actionValidator.isEmpty()) {
                String type = plan.getAction().getType();
                errors.add("action.type is not supported: " + (type == null ? "<null>" : type));
                log.warn("Validation failed: no validator found for action type '{}'", type);
            } else {
                //after verifying which type of action this is it will trigger
                //it will trigger validation corresponding to that action
                actionValidator.get().validate(plan.getAction(), errors);
                log.debug("Action validation completed for type: {}", plan.getAction().getType());
            }
        }

        if (!errors.isEmpty()) {
            log.error("Plan validation failed for executionId: {} with {} errors: {}", 
                    plan.getExecutionId(), errors.size(), String.join(", ", errors));
            throw new AgentException("VALIDATION_ERROR",
                    "Plan validation failed with " + errors.size() + " error(s):\n" + String.join("\n", errors));
        }
        
        log.info("Plan validation successful for executionId: {}", plan.getExecutionId());
    }
}
