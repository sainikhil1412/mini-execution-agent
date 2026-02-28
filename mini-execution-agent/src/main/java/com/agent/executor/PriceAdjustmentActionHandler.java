package com.agent.executor;

import com.agent.model.Action;
import com.agent.model.AuditEntry;
import com.agent.model.PriceAdjustmentAction;
import com.agent.model.ProductRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PriceAdjustmentActionHandler implements ActionHandler {

    @Override
    public boolean supports(Action action) {
        return action instanceof PriceAdjustmentAction;
    }

    @Override
    public AuditEntry apply(ProductRecord record, Action action, boolean dryRun) {
        PriceAdjustmentAction a = (PriceAdjustmentAction) action;

        double before = record.getPrice();
        double after = applyPriceAdjustment(before, a);

        AuditEntry entry = new AuditEntry();
        entry.setSku(record.getSku());
        entry.setField("price");
        entry.setBefore(before);
        entry.setAfter(after);
        entry.setActionApplied(buildActionLabel(a));

        if (dryRun) {
            entry.setStatus("DRY_RUN");
        } else {
            record.setPrice(after);
            entry.setStatus("CHANGED");
        }
        return entry;
    }

    @Override
    public AuditEntry buildSkippedEntry(ProductRecord record, String reason) {
        AuditEntry entry = new AuditEntry();
        entry.setSku(record.getSku());
        entry.setField("price");
        entry.setBefore(record.getPrice());
        entry.setAfter(record.getPrice());
        entry.setStatus("SKIPPED");
        entry.setSkipReason(reason);
        return entry;
    }

    @Override
    public String buildSummary(Action action, List<AuditEntry> changes, List<AuditEntry> skipped, boolean dryRun) {
        PriceAdjustmentAction a = (PriceAdjustmentAction) action;

        double totalDelta = changes.stream()
                .mapToDouble(e -> e.getAfter() - e.getBefore())
                .sum();

        String prefix = dryRun ? "[DRY RUN] " : "";
        return String.format(
                "%s%d product(s) %s. %d product(s) skipped (did not match filters). " +
                        "Net price change: %.2f. Action: %s %.1f%s.",
                prefix,
                changes.size(),
                dryRun ? "would be updated" : "updated",
                skipped.size(),
                totalDelta,
                a.getDirection().name(),
                a.getValue(),
                a.getAdjustmentMode() == PriceAdjustmentAction.AdjustmentMode.PERCENTAGE ? "%" : " (flat)"
        );
    }

    private String buildActionLabel(PriceAdjustmentAction action) {
        return action.getDirection().name() + "_" +
                (int) action.getValue() + "_" +
                action.getAdjustmentMode().name();
    }

    private double applyPriceAdjustment(double currentPrice, PriceAdjustmentAction action) {
        double delta;

        if (action.getAdjustmentMode() == PriceAdjustmentAction.AdjustmentMode.PERCENTAGE) {
            delta = currentPrice * (action.getValue() / 100.0);
        } else {
            // FLAT
            delta = action.getValue();
        }

        double newPrice = action.getDirection() == PriceAdjustmentAction.Direction.INCREASE
                ? currentPrice + delta
                : currentPrice - delta;

        newPrice = Math.max(0.0, newPrice);

        return BigDecimal.valueOf(newPrice)
                .setScale(action.getDecimalPlaces(), RoundingMode.HALF_UP)
                .doubleValue();
    }
}
