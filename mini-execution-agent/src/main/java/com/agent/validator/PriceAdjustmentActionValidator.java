package com.agent.validator;

import com.agent.model.Action;
import com.agent.model.PriceAdjustmentAction;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceAdjustmentActionValidator implements ActionValidator {

    private static final List<String> SUPPORTED_TYPES = List.of(PriceAdjustmentAction.TYPE);

    @Override
    public boolean supports(Action action) {
        return action != null && SUPPORTED_TYPES.contains(action.getType());
    }

    @Override
    public void validate(Action action, List<String> errors) {
        if (!(action instanceof PriceAdjustmentAction)) {
            errors.add("action.type must map to a PriceAdjustmentAction payload.");
            return;
        }

        PriceAdjustmentAction a = (PriceAdjustmentAction) action;

        if (a.getType() == null || !PriceAdjustmentAction.TYPE.equals(a.getType())) {
            errors.add("action.type must be " + PriceAdjustmentAction.TYPE + ".");
        }
        if (a.getAdjustmentMode() == null) {
            errors.add("action.adjustmentMode is required.");
        }
        if (a.getValue() <= 0) {
            errors.add("action.value must be positive. Got: " + a.getValue());
        }
        if (a.getDirection() == null) {
            errors.add("action.direction is required.");
        }
        if (a.getDecimalPlaces() < 0) {
            errors.add("action.decimalPlaces must be >= 0.");
        }
        if (a.getAdjustmentMode() == PriceAdjustmentAction.AdjustmentMode.PERCENTAGE && a.getValue() > 100) {
            errors.add("action.value cannot exceed 100 when adjustmentMode is PERCENTAGE.");
        }
    }
}
