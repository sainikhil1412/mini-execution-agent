package com.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecord {

    private String sku;
    private String category;
    private double price;
    private boolean inStock;

    // Returns field value as String for filter matching
    public String getFieldValue(String fieldName) {
        return switch (fieldName.toLowerCase()) {
            case "sku"       -> sku;
            case "category"  -> category;
            case "price"     -> String.valueOf(price);
            case "in_stock"  -> String.valueOf(inStock);
            default          -> throw new IllegalArgumentException("Unknown field: " + fieldName);
        };
    }
}
