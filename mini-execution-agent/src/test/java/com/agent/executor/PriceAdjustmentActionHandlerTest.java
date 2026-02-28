package com.agent.executor;

import com.agent.model.Action;
import com.agent.model.AuditEntry;
import com.agent.model.PriceAdjustmentAction;
import com.agent.model.ProductRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceAdjustmentActionHandler Tests")
class PriceAdjustmentActionHandlerTest {

    private PriceAdjustmentActionHandler actionHandler;

    @BeforeEach
    void setUp() {
        actionHandler = new PriceAdjustmentActionHandler();
    }

    @Test
    @DisplayName("Should support PriceAdjustmentAction")
    void shouldSupportPriceAdjustmentAction() {
        // Given
        Action priceAction = new PriceAdjustmentAction();

        // When & Then
        assertTrue(actionHandler.supports(priceAction));
    }

    @Test
    @DisplayName("Should not support other action types")
    void shouldNotSupportOtherActionTypes() {
        // Given
        Action mockAction = new Action() {
            @Override
            public String getType() {
                return "OTHER_ACTION";
            }
        };

        // When & Then
        assertFalse(actionHandler.supports(mockAction));
    }

    @Test
    @DisplayName("Should apply percentage increase successfully")
    void shouldApplyPercentageIncreaseSuccessfully() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals("TEST001", result.getSku());
        assertEquals("price", result.getField());
        assertEquals(29.99, result.getBefore());
        assertEquals(32.99, result.getAfter()); // 29.99 + 10% = 32.989 -> 32.99
        assertEquals("CHANGED", result.getStatus());
        assertEquals("INCREASE_10_PERCENTAGE", result.getActionApplied());
        
        // Verify record was updated
        assertEquals(32.99, record.getPrice());
    }

    @Test
    @DisplayName("Should apply percentage decrease successfully")
    void shouldApplyPercentageDecreaseSuccessfully() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.DECREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(29.99, result.getBefore());
        assertEquals(26.99, result.getAfter()); // 29.99 - 10% = 26.991 -> 26.99
        assertEquals("CHANGED", result.getStatus());
        assertEquals("DECREASE_10_PERCENTAGE", result.getActionApplied());
        
        // Verify record was updated
        assertEquals(26.99, record.getPrice());
    }

    @Test
    @DisplayName("Should apply flat increase successfully")
    void shouldApplyFlatIncreaseSuccessfully() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.FLAT,
                5.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(29.99, result.getBefore());
        assertEquals(34.99, result.getAfter()); // 29.99 + 5.0 = 34.99
        assertEquals("CHANGED", result.getStatus());
        assertEquals("INCREASE_5_FLAT", result.getActionApplied());
        
        // Verify record was updated
        assertEquals(34.99, record.getPrice());
    }

    @Test
    @DisplayName("Should apply flat decrease successfully")
    void shouldApplyFlatDecreaseSuccessfully() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.FLAT,
                5.0,
                PriceAdjustmentAction.Direction.DECREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(29.99, result.getBefore());
        assertEquals(24.99, result.getAfter()); // 29.99 - 5.0 = 24.99
        assertEquals("CHANGED", result.getStatus());
        assertEquals("DECREASE_5_FLAT", result.getActionApplied());
        
        // Verify record was updated
        assertEquals(24.99, record.getPrice());
    }

    @Test
    @DisplayName("Should handle dry run mode")
    void shouldHandleDryRunMode() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, true);

        // Then
        assertNotNull(result);
        assertEquals(29.99, result.getBefore());
        assertEquals(32.99, result.getAfter());
        assertEquals("DRY_RUN", result.getStatus());
        assertEquals("INCREASE_10_PERCENTAGE", result.getActionApplied());
        
        // Verify record was NOT updated
        assertEquals(29.99, record.getPrice());
    }

    @Test
    @DisplayName("Should prevent negative prices")
    void shouldPreventNegativePrices() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 5.0, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.FLAT,
                10.0,
                PriceAdjustmentAction.Direction.DECREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(5.0, result.getBefore());
        assertEquals(0.0, result.getAfter()); // Should be clamped to 0.0
        assertEquals("CHANGED", result.getStatus());
        
        // Verify record was updated to 0.0
        assertEquals(0.0, record.getPrice());
    }

    @Test
    @DisplayName("Should handle zero price")
    void shouldHandleZeroPrice() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 0.0, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getBefore());
        assertEquals(0.0, result.getAfter()); // 0.0 + 10% = 0.0
        assertEquals("CHANGED", result.getStatus());
        
        // Verify record remains 0.0
        assertEquals(0.0, record.getPrice());
    }

    @Test
    @DisplayName("Should respect decimal places")
    void shouldRespectDecimalPlaces() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                33.3333, // This will create a repeating decimal
                PriceAdjustmentAction.Direction.INCREASE,
                3 // 3 decimal places
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(29.99, result.getBefore());
        assertEquals(39.987, result.getAfter()); // 29.99 + 33.3333% = 39.98666667 -> 39.987
        assertEquals("CHANGED", result.getStatus());
        
        // Verify record was updated with correct precision
        assertEquals(39.987, record.getPrice());
    }

    @Test
    @DisplayName("Should build skipped entry")
    void shouldBuildSkippedEntry() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 29.99, true);
        String reason = "Did not match filters";

        // When
        AuditEntry result = actionHandler.buildSkippedEntry(record, reason);

        // Then
        assertNotNull(result);
        assertEquals("TEST001", result.getSku());
        assertEquals("price", result.getField());
        assertEquals(29.99, result.getBefore());
        assertEquals(29.99, result.getAfter()); // Should be unchanged
        assertEquals("SKIPPED", result.getStatus());
        assertEquals(reason, result.getSkipReason());
    }

    @Test
    @DisplayName("Should build summary for percentage increase")
    void shouldBuildSummaryForPercentageIncrease() {
        // Given
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );
        
        List<AuditEntry> changes = List.of(
                createAuditEntry("TEST001", 29.99, 32.99),
                createAuditEntry("TEST002", 39.99, 43.99)
        );
        
        List<AuditEntry> skipped = List.of(
                createAuditEntry("TEST003", 19.99, 19.99)
        );

        // When
        String summary = actionHandler.buildSummary(action, changes, skipped, false);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("2 product(s) updated"));
        assertTrue(summary.contains("1 product(s) skipped"));
        assertTrue(summary.contains("Net price change: 6.00"));
        assertTrue(summary.contains("INCREASE 10.0%"));
    }

    @Test
    @DisplayName("Should build summary for flat decrease")
    void shouldBuildSummaryForFlatDecrease() {
        // Given
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.FLAT,
                5.0,
                PriceAdjustmentAction.Direction.DECREASE,
                2
        );
        
        List<AuditEntry> changes = List.of(
                createAuditEntry("TEST001", 29.99, 24.99),
                createAuditEntry("TEST002", 39.99, 34.99)
        );
        
        List<AuditEntry> skipped = List.of();

        // When
        String summary = actionHandler.buildSummary(action, changes, skipped, false);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("2 product(s) updated"));
        assertTrue(summary.contains("0 product(s) skipped"));
        assertTrue(summary.contains("Net price change: -10.00"));
        assertTrue(summary.contains("DECREASE 5.0 (flat)"));
    }

    @Test
    @DisplayName("Should build dry run summary")
    void shouldBuildDryRunSummary() {
        // Given
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );
        
        List<AuditEntry> changes = List.of(
                createAuditEntry("TEST001", 29.99, 32.99)
        );
        
        List<AuditEntry> skipped = List.of();

        // When
        String summary = actionHandler.buildSummary(action, changes, skipped, true);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("[DRY RUN]"));
        assertTrue(summary.contains("1 product(s) would be updated"));
        assertTrue(summary.contains("0 product(s) skipped"));
    }

    @Test
    @DisplayName("Should handle empty changes and skipped lists")
    void shouldHandleEmptyChangesAndSkippedLists() {
        // Given
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                10.0,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );
        
        List<AuditEntry> changes = List.of();
        List<AuditEntry> skipped = List.of();

        // When
        String summary = actionHandler.buildSummary(action, changes, skipped, false);

        // Then
        assertNotNull(summary);
        assertTrue(summary.contains("0 product(s) updated"));
        assertTrue(summary.contains("0 product(s) skipped"));
        assertTrue(summary.contains("Net price change: 0.00"));
    }

    @Test
    @DisplayName("Should handle large percentage values")
    void shouldHandleLargePercentageValues() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 100.0, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                150.0, // 150% increase
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(100.0, result.getBefore());
        assertEquals(250.0, result.getAfter()); // 100.0 + 150% = 250.0
        assertEquals("CHANGED", result.getStatus());
    }

    @Test
    @DisplayName("Should handle precise decimal rounding")
    void shouldHandlePreciseDecimalRounding() {
        // Given
        ProductRecord record = new ProductRecord("TEST001", "fitness", 1.99, true);
        PriceAdjustmentAction action = new PriceAdjustmentAction(
                PriceAdjustmentAction.AdjustmentMode.PERCENTAGE,
                33.33,
                PriceAdjustmentAction.Direction.INCREASE,
                2
        );

        // When
        AuditEntry result = actionHandler.apply(record, action, false);

        // Then
        assertNotNull(result);
        assertEquals(1.99, result.getBefore());
        assertEquals(2.65, result.getAfter()); // 1.99 + 33.33% = 2.652667 -> 2.65 (rounded)
        assertEquals("CHANGED", result.getStatus());
    }

    private AuditEntry createAuditEntry(String sku, double before, double after) {
        AuditEntry entry = new AuditEntry();
        entry.setSku(sku);
        entry.setField("price");
        entry.setBefore(before);
        entry.setAfter(after);
        entry.setStatus("CHANGED");
        return entry;
    }
}
