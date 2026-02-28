package com.agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {

    private String executionId;      // Unique ID — used for idempotency
    private String version;          // Schema version e.g. "1.0"
    private String instruction;      // Original natural-language instruction (for audit)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private List<Filter> filters;    // All filters ANDed together
    private Action action;           // What to do on matched rows

    private boolean summaryRequired; // Whether to generate human-readable summary
    private boolean dryRun;          // If true, simulate but do NOT write changes
}
