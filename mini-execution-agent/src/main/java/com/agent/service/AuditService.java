package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.AuditEntry;
import com.agent.model.ExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a detailed audit log for every execution.
 * Each log is stored as: output/audit_<execution_id>.json
 */
@Slf4j
@Service
public class AuditService {

    private static final String OUTPUT_DIR = "output/";
    private final ObjectMapper mapper;

    public AuditService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        new File(OUTPUT_DIR).mkdirs();
        log.info("AuditService initialized with output directory: {}", OUTPUT_DIR);
    }

    public String writeAuditLog(ExecutionPlan plan,
                                 List<AuditEntry> changes,
                                 List<AuditEntry> skipped,
                                 String status,
                                 String summary) {
        String filename = OUTPUT_DIR + "audit_" + plan.getExecutionId() + ".json";
        log.debug("Writing audit log for executionId: {} to file: {}", plan.getExecutionId(), filename);

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("execution_id",  plan.getExecutionId());
        log.put("version",       plan.getVersion());
        log.put("status",        status);
        log.put("dry_run",       plan.isDryRun());
        log.put("executed_at",   LocalDateTime.now().toString());
        log.put("instruction",   plan.getInstruction());
        log.put("filters",       plan.getFilters());
        log.put("action",        plan.getAction());
        log.put("summary",       summary);
        log.put("total_changed", changes.size());
        log.put("total_skipped", skipped.size());
        log.put("changes",       changes);
        log.put("skipped",       skipped);

        try {
            mapper.writeValue(new File(filename), log);
            log.info("Successfully wrote audit log for executionId: {} with {} changes, {} skipped", 
                    plan.getExecutionId(), changes.size(), skipped.size());
        } catch (IOException e) {
            log.error("Failed to write audit log for executionId: {} to file: {}", plan.getExecutionId(), filename, e);
            throw new AgentException("AUDIT_ERROR", "Failed to write audit log: " + e.getMessage());
        }

        return filename;
    }
}
