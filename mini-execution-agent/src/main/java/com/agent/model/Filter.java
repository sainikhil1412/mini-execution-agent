package com.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Filter {

    private String field;        // e.g. "category", "in_stock", "price"
    private FilterOperator operator; // EQUALS, NOT_EQUALS, GT, LT, GTE, LTE
    private String value;        // always stored as string, cast during matching

    public enum FilterOperator {
        EQUALS,
        NOT_EQUALS,
        GT,
        LT,
        GTE,
        LTE
    }
}
