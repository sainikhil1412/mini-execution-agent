package com.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustmentAction implements Action {

    public static final String TYPE = "PRICE_ADJUSTMENT";

    private String type = TYPE;
    private AdjustmentMode adjustmentMode; // PERCENTAGE or FLAT
    private double value;                  // e.g. 10.0 for 10%
    private Direction direction;           // INCREASE or DECREASE
    private int decimalPlaces;             // rounding decimal places (default 2)

    public enum AdjustmentMode {
        PERCENTAGE,
        FLAT
    }

    public enum Direction {
        INCREASE,
        DECREASE
    }
}
