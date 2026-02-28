package com.agent.validator;

import com.agent.model.Action;

import java.util.List;

public interface ActionValidator {
    boolean supports(Action action);
    void validate(Action action, List<String> errors);
}
