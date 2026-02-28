package com.agent.executor;

import com.agent.model.Filter;
import com.agent.model.ProductRecord;

/**
 * Utility class that matches a ProductRecord against a single Filter.
 * Supports string EQUALS/NOT_EQUALS and numeric GT/LT/GTE/LTE comparisons.
 * Adding support for a new operator means only changing this class.
 */
public class FilterMatcher {

    public static boolean matches(ProductRecord record, Filter filter) {
        String fieldValue = record.getFieldValue(filter.getField().toLowerCase());
        String filterValue = filter.getValue();

        return switch (filter.getOperator()) {
            case EQUALS     -> fieldValue.equalsIgnoreCase(filterValue);
            case NOT_EQUALS -> !fieldValue.equalsIgnoreCase(filterValue);
            case GT         -> toDouble(fieldValue) > toDouble(filterValue);
            case LT         -> toDouble(fieldValue) < toDouble(filterValue);
            case GTE        -> toDouble(fieldValue) >= toDouble(filterValue);
            case LTE        -> toDouble(fieldValue) <= toDouble(filterValue);
        };
    }

    private static double toDouble(String value) {
        // handle boolean strings like "true"/"false"
        if ("true".equalsIgnoreCase(value))  return 1.0;
        if ("false".equalsIgnoreCase(value)) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot compare non-numeric value: " + value);
        }
    }
}
