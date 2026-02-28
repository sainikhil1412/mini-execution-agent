package com.agent.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DisplayName("Integration Tests")
class IntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should execute complete workflow - validate then execute")
    void shouldExecuteCompleteWorkflow() throws Exception {
        String planJson = """
            {
              "executionId": "exec-integration-001",
              "version": "1.0",
              "instruction": "Increase prices by 10% for all in-stock fitness products",
              "createdAt": "2025-02-28T10:00:00",
              "filters": [
                {
                  "field": "category",
                  "operator": "EQUALS",
                  "value": "fitness"
                },
                {
                  "field": "in_stock",
                  "operator": "EQUALS",
                  "value": "true"
                }
              ],
              "action": {
                "type": "PRICE_ADJUSTMENT",
                "adjustmentMode": "PERCENTAGE",
                "value": 10.0,
                "direction": "INCREASE",
                "decimalPlaces": 2
              },
              "summaryRequired": true,
              "dryRun": false
            }
            """;

        // Step 1: Validate the plan
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.plan.executionId").value("exec-integration-001"));

        // Step 2: Execute the plan
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("exec-integration-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.changedRecords").value(2))
                .andExpect(jsonPath("$.skippedRecords").value(4));

        // Step 3: Check execution status
        mockMvc.perform(get("/api/execute/exec-integration-001/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").value("exec-integration-001"))
                .andExpect(jsonPath("$.already_executed").value(true));

        // Step 4: Try to execute again (should be skipped)
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.message").value("This execution_id has already been completed."));

        // Step 5: Check execution history
        mockMvc.perform(get("/api/execute/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exec-integration-001.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Should handle dry run execution")
    void shouldHandleDryRunExecution() throws Exception {
        String dryRunPlanJson = """
            {
              "executionId": "exec-dry-run-001",
              "version": "1.0",
              "instruction": "Increase prices by 10% for all in-stock fitness products",
              "createdAt": "2025-02-28T10:00:00",
              "filters": [
                {
                  "field": "category",
                  "operator": "EQUALS",
                  "value": "fitness"
                },
                {
                  "field": "in_stock",
                  "operator": "EQUALS",
                  "value": "true"
                }
              ],
              "action": {
                "type": "PRICE_ADJUSTMENT",
                "adjustmentMode": "PERCENTAGE",
                "value": 10.0,
                "direction": "INCREASE",
                "decimalPlaces": 2
              },
              "summaryRequired": true,
              "dryRun": true
            }
            """;

        // Execute dry run
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunPlanJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("exec-dry-run-001"))
                .andExpect(jsonPath("$.status").value("DRY_RUN"))
                .andExpect(jsonPath("$.message").value("Dry run completed. No data was modified."))
                .andExpect(jsonPath("$.summary").value("[DRY RUN] 2 product(s) would be updated. 4 product(s) skipped."));

        // Check that it's still marked as executed (even dry runs are tracked)
        mockMvc.perform(get("/api/execute/exec-dry-run-001/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.already_executed").value(true));
    }

    @Test
    @DisplayName("Should handle validation errors gracefully")
    void shouldHandleValidationErrorsGracefully() throws Exception {
        String invalidPlanJson = """
            {
              "executionId": "",
              "version": "2.0",
              "instruction": "Test instruction",
              "createdAt": "2025-02-28T10:00:00",
              "filters": [],
              "action": {
                "type": "INVALID_ACTION",
                "adjustmentMode": "PERCENTAGE",
                "value": 10.0,
                "direction": "INCREASE",
                "decimalPlaces": 2
              },
              "summaryRequired": true,
              "dryRun": false
            }
            """;

        // Should return validation error
        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPlanJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should handle malformed JSON")
    void shouldHandleMalformedJson() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/api/plan/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should generate plan from instruction")
    void shouldGeneratePlanFromInstruction() throws Exception {
        String instruction = "Increase prices by 10% for all in-stock fitness products";

        mockMvc.perform(post("/api/plan/generate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(instruction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.plan.executionId").exists())
                .andExpect(jsonPath("$.plan.version").value("1.0"))
                .andExpect(jsonPath("$.plan.instruction").value(instruction))
                .andExpect(jsonPath("$.plan.action.type").value("PRICE_ADJUSTMENT"))
                .andExpect(jsonPath("$.plan.action.value").value(10.0))
                .andExpect(jsonPath("$.plan.filters").isArray());
    }
}
