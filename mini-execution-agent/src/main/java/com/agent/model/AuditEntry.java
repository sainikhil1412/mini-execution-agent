package com.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {

    private String sku;
    private String field;
    private double before;
    private double after;
    private String actionApplied;
    private String status; // CHANGED or SKIPPED
    private String skipReason; // populated if SKIPPED
}
