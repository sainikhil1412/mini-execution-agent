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
public class ExecutionResult {

    private String executionId;
    private String status;           // COMPLETED, SKIPPED, DRY_RUN, FAILED
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime executedAt;

    private int totalRecords;
    private int matchedRecords;
    private int changedRecords;
    private int skippedRecords;

    private List<AuditEntry> changes;
    private List<AuditEntry> skipped;
    private String summary;          // Human-readable summary
    private String auditLogPath;     // Path to written audit log
    private String outputCsvName;    // New CSV file name (if written)
    private String outputCsvPath;    // New CSV absolute/relative path (if written)

    public static ExecutionResult skipped(String executionId) {
        ExecutionResult r = new ExecutionResult();
        r.setExecutionId(executionId);
        r.setStatus("SKIPPED");
        r.setMessage("Execution already completed. Skipping to ensure idempotency.");
        r.setExecutedAt(LocalDateTime.now());
        return r;
    }
}
