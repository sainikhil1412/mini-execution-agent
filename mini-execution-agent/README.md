# Mini Execution Agent

A local execution agent that safely translates high-level natural-language instructions
into auditable, idempotent pricing changes on a CSV dataset.

Built with **Java 17 + Spring Boot 3.x**.

---

## Architecture

```
Natural Language Instruction
        │
        ▼
   [PLANNER]  ──── Plan Generation (LLM integration ready)
        │           using PlannerPromptTemplates
        ▼
   JSON Execution Plan
        │
        ▼
   [EXECUTOR]
   1. Validate plan against schema + business rules
   2. Check idempotency (skip if execution_id already completed)
   3. Load products.csv
   4. Filter rows (ALL filters ANDed)
   5. Apply action to matched rows
   6. Write updated CSV (unless dry_run=true)
   7. Write audit log JSON
   8. Persist execution_id as COMPLETED
        │
        ▼
   Updated CSV + Audit Log + ExecutionResult
```

### Planner vs Executor

| | Planner | Executor |
|---|---|---|
| **Input** | Natural language instruction | JSON ExecutionPlan |
| **Output** | JSON ExecutionPlan | Updated CSV + Audit log |
| **Knows about** | Business intent | Filters and actions only |
| **LLM dependency** | Yes (manual in this impl) | None |

The executor has **zero knowledge** of "fitness" or "10%". It only reads filters and
actions from the plan. Adding a new pricing scenario requires only a new plan JSON —
no executor code changes.

---

## Project Structure

```
mini-execution-agent/
├── src/main/java/com/agent/
│   ├── MiniAgentApplication.java
│   ├── controller/
│   │   ├── ExecutorController.java     # POST /api/execute
│   │   ├── PlannerController.java      # POST /api/plan/validate, /api/plan/generate
│   │   └── SwaggerRedirectController.java # Swagger UI redirect
│   ├── model/
│   │   ├── ExecutionPlan.java
│   │   ├── Filter.java
│   │   ├── Action.java
│   │   ├── PriceAdjustmentAction.java
│   │   ├── ProductRecord.java
│   │   ├── AuditEntry.java
│   │   └── ExecutionResult.java
│   ├── service/
│   │   ├── PlannerService.java
│   │   ├── ExecutorService.java
│   │   ├── CsvService.java
│   │   ├── AuditService.java
│   │   ├── IdempotencyService.java
│   │   └── PlanGenerationService.java   # LLM integration ready
│   ├── validator/
│   │   ├── PlanValidator.java
│   │   ├── ActionValidator.java
│   │   └── PriceAdjustmentActionValidator.java
│   ├── executor/
│   │   ├── FilterMatcher.java
│   │   ├── ActionHandler.java
│   │   └── PriceAdjustmentActionHandler.java
│   ├── llm/
│   │   └── PlannerPromptTemplates.java
│   └── exception/
│       ├── AgentException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.properties
│   ├── data/products.csv               # Input dataset
│   └── schema/execution-plan-schema.json
├── src/test/                           # Comprehensive test suite
│   └── java/com/agent/
│       ├── controller/, service/, validator/, executor/, exception/
│       └── integration/
├── output/
│   ├── products_updated.csv            # Written after execution
│   └── audit_<executionId>.json        # Audit log per execution
├── prompts/
│   ├── architecture_prompt.md
│   ├── planner_prompt.md
│   └── executor_prompt.md
├── example-plan.json                   # Required scenario plan
├── example-plans-additional.json       # Additional scenarios (extensibility demo)
├── TEST_DOCUMENTATION.md               # Complete test suite documentation
└── README.md
```

---

## Running the Project

### Prerequisites
- Java 17+
- Maven 3.8+

### Start the server

```bash
mvn spring-boot:run
```

Server runs at `http://localhost:8080`

---

## API Endpoints

### 1. Generate a Plan from Natural Language
```
POST /api/plan/generate
Content-Type: text/plain

Body: "Increase prices by 10% for all in-stock fitness products"
```

### 2. Validate a Plan
```
POST /api/plan/validate
Content-Type: application/json

Body: (paste the JSON plan)
```

### 3. Execute a Plan
```
POST /api/execute
Content-Type: application/json

Body: (paste the JSON plan)
```

### 4. Check Execution Status (Idempotency)
```
GET /api/execute/{executionId}/status
```

### 5. View Execution History
```
GET /api/execute/history
```

### 6. API Documentation (Swagger UI)
```
GET /swagger-ui.html
```

### 7. Health Check
```
GET /actuator/health
```

---

## Running the Required Example

**Step 1:** Start the server
```bash
mvn spring-boot:run
```

**Step 2:** Execute the plan using curl or Postman
```bash
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -d @example-plan.json
```

**Step 3:** Check idempotency (run again — should return SKIPPED)
```bash
curl -X POST http://localhost:8080/api/execute \
  -H "Content-Type: application/json" \
  -d @example-plan.json
```

**Step 4:** Check outputs
- Updated CSV: `output/products_updated.csv`
- Audit log:   `output/audit_exec-20250228-001.json`

---

## Expected Results for Required Scenario

**Instruction:** "Increase prices by 10% for all in-stock fitness products."

| SKU | Category | Before | After | Status |
|-----|----------|--------|-------|--------|
| A101 | fitness | 29.99 | 32.99 | CHANGED |
| A102 | fitness | 39.99 | 43.99 | CHANGED |
| A103 | fitness | 49.99 | 49.99 | SKIPPED (out of stock) |
| B201 | yoga    | 19.99 | 19.99 | SKIPPED |
| B202 | yoga    | 24.99 | 24.99 | SKIPPED |
| C301 | accessories | 9.99 | 9.99 | SKIPPED |
| C302 | accessories | 14.99 | 14.99 | SKIPPED |

---

## Testing

### Comprehensive Test Suite
- **Unit Tests**: 95%+ coverage with JUnit 5 + Mockito
- **Integration Tests**: Complete API workflow testing
- **Test Documentation**: See [TEST_DOCUMENTATION.md](TEST_DOCUMENTATION.md)

### Run Tests
```bash
mvn test                    # Run all tests
mvn test jacoco:report     # Run with coverage report
mvn test -Dtest=PlannerServiceTest  # Run specific test class
```

### Test Coverage
- Controllers: Request/response handling, error scenarios
- Services: Business logic, edge cases, error handling
- Validators: Input validation, business rules
- Utilities: Data processing, file operations
- Integration: End-to-end API workflows

---

## Technology Stack

### Core Technologies
- **Java 17** - Modern Java features and performance
- **Spring Boot 3.x** - Enterprise application framework
- **Spring Web** - REST API development
- **Spring Boot Actuator** - Application monitoring

### Data Processing
- **OpenCSV** - CSV file reading and writing
- **Jackson** - JSON serialization/deserialization
- **Java Time API** - Date/time handling

### Code Quality
- **Lombok** - Boilerplate code reduction
- **SLF4j + Logback** - Comprehensive logging framework
- **Bean Validation** - Input validation

### Testing & Documentation
- **JUnit 5** - Modern testing framework
- **Mockito** - Mocking framework for unit tests
- **Spring Boot Test** - Integration testing support
- **SpringDoc OpenAPI** - API documentation (Swagger UI)

### Build & Dependency Management
- **Maven 3.8+** - Build tool and dependency management
- **Maven Surefire** - Test execution
- **JaCoCo** - Code coverage reporting

---

## Idempotency

Each plan has a unique `executionId`. The executor checks `state/execution_state.json`
before running. If the ID is already marked `COMPLETED`, execution is skipped immediately.
This makes every API call safe under retries.

```json
// state/execution_state.json (auto-managed)
{
  "exec-20250228-001": {
    "status": "COMPLETED",
    "executed_at": "2025-02-28T10:05:00"
  }
}
```

---

## Extending to New Scenarios

No executor code changes are needed. Just provide a new plan JSON:

```json
// Discount yoga by 15%
{
  "executionId": "exec-20250228-002",
  "version": "1.0",
  "instruction": "Decrease prices by 15% for all yoga products.",
  "createdAt": "2025-02-28T11:00:00",
  "filters": [{ "field": "category", "operator": "EQUALS", "value": "yoga" }],
  "action": { "type": "PRICE_ADJUSTMENT", "adjustmentMode": "PERCENTAGE",
               "value": 15.0, "direction": "DECREASE", "decimalPlaces": 2 },
  "summaryRequired": true,
  "dryRun": false
}
```

See `example-plans-additional.json` for more scenarios.

---

## Dry Run Mode

Set `"dryRun": true` in the plan to preview changes without modifying the CSV.
The audit log is still written and the execution is tracked for idempotency.

---

## Logging & Monitoring

### Comprehensive Logging
The system implements **SLF4j + Logback** with structured logging:
- **INFO Level**: Key business events, successful operations
- **DEBUG Level**: Detailed execution flow, intermediate steps
- **WARN Level**: Recoverable issues, validation failures
- **ERROR Level**: Critical failures, exceptions

### Log Examples
```bash
# Application startup
INFO  Starting Mini Execution Agent application...

# Plan execution
INFO  Starting execution for executionId: exec-001, dryRun: false
DEBUG Loaded 6 total records from CSV
INFO  Filtered records: 2 matched, 4 unmatched
INFO  Execution completed successfully for executionId: exec-001, status: COMPLETED

# Error scenarios
ERROR Plan validation failed for executionId: exec-002 with 2 errors
```

### Monitoring Endpoints
- **Health Check**: `GET /actuator/health`
- **Application Info**: `GET /actuator/info`
- **Metrics**: `GET /actuator/metrics` (if enabled)

### Audit Trail
Every execution creates a comprehensive audit log:
```json
{
  "execution_id": "exec-001",
  "status": "COMPLETED",
  "executed_at": "2025-02-28T10:05:00",
  "total_changed": 2,
  "total_skipped": 4,
  "changes": [...],
  "skipped": [...]
}
```

---

## Development & Contributing

### Code Quality Standards
- **SLF4j Logging**: Comprehensive logging at appropriate levels
- **Exception Handling**: Custom exceptions with proper error codes
- **Input Validation**: Bean validation + custom validators
- **Test Coverage**: 95%+ coverage required for new features

### Adding New Actions
1. Create new action class extending `Action`
2. Implement corresponding `ActionHandler`
3. Add `ActionValidator` for the new action type
4. Write comprehensive tests
5. Update documentation

### Adding New Filters
1. Add filter logic to `FilterMatcher.java`
2. Update validation rules in `PlanValidator.java`
3. Add test cases for new filter operators
4. Update documentation

---

## LLM Integration

### Current Implementation
The system includes a **PlanGenerationService** that's ready for LLM integration:
- **PlannerPromptTemplates** - Structured prompt templates for consistent LLM interactions
- **Mock Implementation** - Currently generates plans from natural language with predefined rules
- **Retry Logic** - Built-in retry mechanism with exponential backoff
- **Validation Integration** - Generated plans are automatically validated

### Production Upgrade Path
To integrate with a real LLM (OpenAI, Claude, etc.):

```java
// In PlanGenerationService.java - replace mockLlmGeneratePlan method
private String mockLlmGeneratePlan(String prompt, String instruction) {
    // Replace with actual LLM API call
    return openAiClient.generateCompletion(prompt);
}
```

### Key Features
- **Zero Executor Changes** - LLM integration only affects the planner
- **Consistent Prompts** - Template-based approach ensures reliable output
- **Error Handling** - Graceful fallback when LLM is unavailable
- **Validation Pipeline** - All generated plans pass through the same validation

The executor requires **zero changes** for this upgrade.
