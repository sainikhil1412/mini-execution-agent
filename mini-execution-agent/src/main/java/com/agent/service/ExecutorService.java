package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.executor.ActionHandler;
import com.agent.executor.FilterMatcher;
import com.agent.model.*;
import com.agent.validator.PlanValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core Executor Service.
 *
 * Flow:
 * 1. Validate the plan
 * 2. Check idempotency — skip if already executed
 * 3. Load CSV
 * 4. Filter rows using plan.filters (all filters ANDed)
 * 5. Apply plan.action to each matched row
 * 6. Write updated CSV (unless dry_run=true)
 * 7. Write audit log
 * 8. Mark execution as completed
 * 9. Return ExecutionResult
 *
 * The executor has ZERO knowledge of "fitness" or "10%" —
 * it only understands filters and actions from the plan.
 */
@Slf4j
@Service
public class ExecutorService {

    @Value("${agent.data.input-path:src/main/resources/data/products.csv}")
    private String inputCsvPath;

    @Value("${agent.data.output-path:output/products_updated.csv}")
    private String outputCsvPath;

    private final PlanValidator planValidator;
    private final IdempotencyService idempotencyService;
    private final CsvService csvService;
    private final AuditService auditService;
    private final List<ActionHandler> actionHandlers;

    public ExecutorService(PlanValidator planValidator,
                           IdempotencyService idempotencyService,
                           CsvService csvService,
                           AuditService auditService,
                           List<ActionHandler> actionHandlers) {
        this.planValidator      = planValidator;
        this.idempotencyService = idempotencyService;
        this.csvService         = csvService;
        this.auditService       = auditService;
        this.actionHandlers     = actionHandlers;
        log.info("ExecutorService initialized with {} action handlers", actionHandlers.size());
        log.debug("Input CSV path: {}, Output CSV path: {}", inputCsvPath, outputCsvPath);
    }

    public ExecutionResult execute(ExecutionPlan plan) {
        log.info("Starting execution for executionId: {}, dryRun: {}", plan.getExecutionId(), plan.isDryRun());
        
        // Step 1: Validate plan
        log.debug("Validating execution plan");
        planValidator.validateExecutionPlan(plan);

        // Step 2: Idempotency check
        if (idempotencyService.alreadyExecuted(plan.getExecutionId())) {
            log.info("Execution skipped - already executed for executionId: {}", plan.getExecutionId());
            return ExecutionResult.skipped(plan.getExecutionId());
        }

        // Step 3: Load CSV
        log.debug("Loading CSV from path: {}", inputCsvPath);
        List<ProductRecord> allRecords = csvService.readCsv(inputCsvPath);
        log.info("Loaded {} total records from CSV", allRecords.size());

        // Step 4: Partition records into matched vs unmatched
        List<ProductRecord> matched = allRecords.stream()
                .filter(r -> matchesAllFilters(r, plan.getFilters()))
                .collect(Collectors.toList());

        List<ProductRecord> unmatched = allRecords.stream()
                .filter(r -> !matchesAllFilters(r, plan.getFilters()))
                .collect(Collectors.toList());
        
        log.info("Filtered records: {} matched, {} unmatched", matched.size(), unmatched.size());

        ActionHandler actionHandler = resolveHandler(plan.getAction());
        log.debug("Resolved action handler: {}", actionHandler.getClass().getSimpleName());

        // Step 5: Apply action to matched rows, build audit entries
        List<AuditEntry> changes  = new ArrayList<>();
        List<AuditEntry> skipped  = new ArrayList<>();

        for (ProductRecord record : matched) {
            changes.add(actionHandler.apply(record, plan.getAction(), plan.isDryRun()));
        }
        
        log.debug("Applied action to {} matched records", changes.size());

        // Build skipped entries for unmatched records
        for (ProductRecord record : unmatched) {
            skipped.add(actionHandler.buildSkippedEntry(record, "Did not match all filters"));
        }

        // Step 6: Write updated CSV (skip if dry run)
        String outputPath = null;
        String outputName = null;
        if (!plan.isDryRun()) {
            Path newOutputPath = buildOutputPath(plan.getExecutionId());
            outputPath = newOutputPath.toString();
            outputName = newOutputPath.getFileName().toString();
            log.debug("Writing updated CSV to: {}", outputPath);
            csvService.writeCsvAtomic(allRecords, outputPath);
            log.info("Successfully wrote updated CSV to: {}", outputPath);
        } else {
            log.info("Dry run - skipping CSV write");
        }

        // Step 7: Build summary
        String summary = plan.isSummaryRequired()
                ? actionHandler.buildSummary(plan.getAction(), changes, skipped, plan.isDryRun())
                : null;
        
        if (summary != null) {
            log.debug("Generated execution summary");
        }

        // Step 8: Write audit log
        String status = plan.isDryRun() ? "DRY_RUN" : "COMPLETED";
        log.debug("Writing audit log with status: {}", status);
        String auditPath = auditService.writeAuditLog(plan, changes, skipped, status, summary);
        log.info("Audit log written to: {}", auditPath);

        // Step 9: Mark as executed (even dry runs are tracked)
        idempotencyService.markExecuted(plan.getExecutionId(), LocalDateTime.now());
        log.debug("Marked execution as completed for executionId: {}", plan.getExecutionId());

        // Step 10: Build and return result
        ExecutionResult result = new ExecutionResult();
        result.setExecutionId(plan.getExecutionId());
        result.setStatus(status);
        result.setMessage(plan.isDryRun()
                ? "Dry run completed. No data was modified."
                : "Execution completed successfully.");
        result.setExecutedAt(LocalDateTime.now());
        result.setTotalRecords(allRecords.size());
        result.setMatchedRecords(matched.size());
        result.setChangedRecords(changes.size());
        result.setSkippedRecords(skipped.size());
        result.setChanges(changes);
        result.setSkipped(skipped);
        result.setSummary(summary);
        result.setAuditLogPath(auditPath);
        result.setOutputCsvPath(outputPath);
        result.setOutputCsvName(outputName);

        log.info("Execution completed successfully for executionId: {}, status: {}, changed records: {}", 
                plan.getExecutionId(), status, changes.size());
        return result;
    }

    // All filters are ANDed together
    private boolean matchesAllFilters(ProductRecord record, List<Filter> filters) {
        return filters.stream().allMatch(f -> FilterMatcher.matches(record, f));
    }

    private ActionHandler resolveHandler(Action action) {
        log.debug("Resolving action handler for action type: {}", action.getType());
        return actionHandlers.stream()
                .filter(h -> h.supports(action))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No action handler found for action type: {}", action.getType());
                    return new AgentException(
                            "VALIDATION_ERROR",
                            "No action handler registered for action.type: " + action.getType()
                    );
                });
    }

    private Path buildOutputPath(String executionId) {
        Path base = Path.of(outputCsvPath);
        String fileName = base.getFileName() == null ? "products_updated.csv" : base.getFileName().toString();

        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot >= 0 ? fileName.substring(dot) : ".csv";

        String newFileName = baseName + "_" + executionId + ext;
        Path dir = base.getParent() == null ? Path.of(".") : base.getParent();
        return dir.resolve(newFileName);
    }
}
