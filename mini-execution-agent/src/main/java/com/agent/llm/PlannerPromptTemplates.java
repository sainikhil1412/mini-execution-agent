package com.agent.llm;

public final class PlannerPromptTemplates {

    private PlannerPromptTemplates() {
    }

    public static final String PLAN_PROMPT_TEMPLATE = """
You are a planning agent. Convert the human instruction into a strict JSON plan.

Supported actions:
- PRICE_ADJUSTMENT
  Fields: type, adjustmentMode (PERCENTAGE|FLAT), value (>0), direction (INCREASE|DECREASE), decimalPlaces (>=0)
  Rule: if adjustmentMode is PERCENTAGE, value must be <= 100.

Supported filter fields: sku, category, price, in_stock
Supported filter operators: EQUALS, NOT_EQUALS, GT, LT, GTE, LTE

Output must be raw JSON only (no markdown). JSON schema:
{
  "executionId": "string",
  "version": "1.0",
  "instruction": "string",
  "createdAt": "yyyy-MM-dd'T'HH:mm:ss",
  "filters": [
    { "field": "category", "operator": "EQUALS", "value": "fitness" }
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

Human instruction:
%s

If the previous attempt failed validation, fix the issues using this error:
%s
""";
}
