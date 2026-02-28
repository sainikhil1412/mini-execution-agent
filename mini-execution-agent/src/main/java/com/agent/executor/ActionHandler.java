package com.agent.executor;

import com.agent.model.Action;
import com.agent.model.AuditEntry;
import com.agent.model.ProductRecord;

import java.util.List;

public interface ActionHandler {
    boolean supports(Action action);
    AuditEntry apply(ProductRecord record, Action action, boolean dryRun);
    AuditEntry buildSkippedEntry(ProductRecord record, String reason);
    String buildSummary(Action action, List<AuditEntry> changes, List<AuditEntry> skipped, boolean dryRun);
}
