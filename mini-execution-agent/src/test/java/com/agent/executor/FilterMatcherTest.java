package com.agent.executor;

import com.agent.model.Filter;
import com.agent.model.ProductRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilterMatcher Tests")
class FilterMatcherTest {

    private ProductRecord testRecord;

    @BeforeEach
    void setUp() {
        testRecord = new ProductRecord("TEST001", "fitness", 29.99, true);
    }

    @Test
    @DisplayName("Should match EQUALS operator for strings")
    void shouldMatchEqualsOperatorForStrings() {
        // Given
        Filter filter = new Filter("category", Filter.FilterOperator.EQUALS, "fitness");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, filter));

        // Test case insensitive
        Filter uppercaseFilter = new Filter("category", Filter.FilterOperator.EQUALS, "FITNESS");
        assertTrue(FilterMatcher.matches(testRecord, uppercaseFilter));

        // Test non-matching value
        Filter nonMatchingFilter = new Filter("category", Filter.FilterOperator.EQUALS, "yoga");
        assertFalse(FilterMatcher.matches(testRecord, nonMatchingFilter));
    }

    @Test
    @DisplayName("Should match NOT_EQUALS operator for strings")
    void shouldMatchNotEqualsOperatorForStrings() {
        // Given
        Filter filter = new Filter("category", Filter.FilterOperator.NOT_EQUALS, "yoga");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, filter));

        // Test case insensitive
        Filter uppercaseFilter = new Filter("category", Filter.FilterOperator.NOT_EQUALS, "YOGA");
        assertTrue(FilterMatcher.matches(testRecord, uppercaseFilter));

        // Test matching value (should return false)
        Filter matchingFilter = new Filter("category", Filter.FilterOperator.NOT_EQUALS, "fitness");
        assertFalse(FilterMatcher.matches(testRecord, matchingFilter));
    }

    @Test
    @DisplayName("Should match EQUALS operator for booleans")
    void shouldMatchEqualsOperatorForBooleans() {
        // Given
        Filter trueFilter = new Filter("in_stock", Filter.FilterOperator.EQUALS, "true");
        Filter falseFilter = new Filter("in_stock", Filter.FilterOperator.EQUALS, "false");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, trueFilter));
        assertFalse(FilterMatcher.matches(testRecord, falseFilter));

        // Test case insensitive
        Filter uppercaseTrueFilter = new Filter("in_stock", Filter.FilterOperator.EQUALS, "TRUE");
        assertTrue(FilterMatcher.matches(testRecord, uppercaseTrueFilter));
    }

    @Test
    @DisplayName("Should match NOT_EQUALS operator for booleans")
    void shouldMatchNotEqualsOperatorForBooleans() {
        // Given
        Filter trueFilter = new Filter("in_stock", Filter.FilterOperator.NOT_EQUALS, "true");
        Filter falseFilter = new Filter("in_stock", Filter.FilterOperator.NOT_EQUALS, "false");

        // When & Then
        assertFalse(FilterMatcher.matches(testRecord, trueFilter));
        assertTrue(FilterMatcher.matches(testRecord, falseFilter));
    }

    @Test
    @DisplayName("Should match numeric comparison operators")
    void shouldMatchNumericComparisonOperators() {
        // Given
        Filter gtFilter = new Filter("price", Filter.FilterOperator.GT, "25.00");
        Filter ltFilter = new Filter("price", Filter.FilterOperator.LT, "35.00");
        Filter gteFilter = new Filter("price", Filter.FilterOperator.GTE, "29.99");
        Filter lteFilter = new Filter("price", Filter.FilterOperator.LTE, "29.99");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, gtFilter));   // 29.99 > 25.00
        assertTrue(FilterMatcher.matches(testRecord, ltFilter));   // 29.99 < 35.00
        assertTrue(FilterMatcher.matches(testRecord, gteFilter));  // 29.99 >= 29.99
        assertTrue(FilterMatcher.matches(testRecord, lteFilter));  // 29.99 <= 29.99

        // Test non-matching conditions
        Filter nonMatchingGt = new Filter("price", Filter.FilterOperator.GT, "30.00");
        assertFalse(FilterMatcher.matches(testRecord, nonMatchingGt)); // 29.99 !> 30.00

        Filter nonMatchingLt = new Filter("price", Filter.FilterOperator.LT, "20.00");
        assertFalse(FilterMatcher.matches(testRecord, nonMatchingLt)); // 29.99 !< 20.00
    }

    @ParameterizedTest
    @DisplayName("Should handle boolean values as numbers in comparisons")
    @CsvSource({
        "in_stock, GT, 0.5, true",   // true (1.0) > 0.5
        "in_stock, GT, 0.9, true",   // true (1.0) > 0.9
        "in_stock, LTE, 1.0, true",  // true (1.0) <= 1.0
        "in_stock, GTE, 1.0, true",  // true (1.0) >= 1.0
        "in_stock, LT, 1.0, false",  // true (1.0) !< 1.0
        "in_stock, GT, 1.0, false"   // true (1.0) !> 1.0
    })
    void shouldHandleBooleanValuesAsNumbersInComparisons(String field, Filter.FilterOperator operator, String value, boolean expected) {
        // Given
        Filter filter = new Filter(field, operator, value);

        // When & Then
        assertEquals(expected, FilterMatcher.matches(testRecord, filter));
    }

    @Test
    @DisplayName("Should handle false boolean values as numbers in comparisons")
    void shouldHandleFalseBooleanValuesAsNumbersInComparisons() {
        // Given
        ProductRecord falseRecord = new ProductRecord("TEST002", "yoga", 19.99, false);
        Filter gtFilter = new Filter("in_stock", Filter.FilterOperator.GT, "0.5");
        Filter ltFilter = new Filter("in_stock", Filter.FilterOperator.LT, "0.5");

        // When & Then
        assertFalse(FilterMatcher.matches(falseRecord, gtFilter)); // false (0.0) !> 0.5
        assertTrue(FilterMatcher.matches(falseRecord, ltFilter));  // false (0.0) < 0.5
    }

    @Test
    @DisplayName("Should throw exception for non-numeric values in numeric comparisons")
    void shouldThrowExceptionForNonNumericValuesInNumericComparisons() {
        // Given
        Filter filter = new Filter("category", Filter.FilterOperator.GT, "some_value");

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            FilterMatcher.matches(testRecord, filter);
        });

        assertTrue(exception.getMessage().contains("Cannot compare non-numeric value"));
    }

    @Test
    @DisplayName("Should handle all supported field names")
    void shouldHandleAllSupportedFieldNames() {
        // Given
        Filter skuFilter = new Filter("sku", Filter.FilterOperator.EQUALS, "TEST001");
        Filter categoryFilter = new Filter("category", Filter.FilterOperator.EQUALS, "fitness");
        Filter priceFilter = new Filter("price", Filter.FilterOperator.GTE, "29.99");
        Filter inStockFilter = new Filter("in_stock", Filter.FilterOperator.EQUALS, "true");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, skuFilter));
        assertTrue(FilterMatcher.matches(testRecord, categoryFilter));
        assertTrue(FilterMatcher.matches(testRecord, priceFilter));
        assertTrue(FilterMatcher.matches(testRecord, inStockFilter));
    }

    @Test
    @DisplayName("Should handle case insensitive field names")
    void shouldHandleCaseInsensitiveFieldNames() {
        // Given
        Filter uppercaseFilter = new Filter("CATEGORY", Filter.FilterOperator.EQUALS, "fitness");
        Filter mixedCaseFilter = new Filter("Category", Filter.FilterOperator.EQUALS, "fitness");

        // When & Then
        assertTrue(FilterMatcher.matches(testRecord, uppercaseFilter));
        assertTrue(FilterMatcher.matches(testRecord, mixedCaseFilter));
    }

    @Test
    @DisplayName("Should handle decimal numbers correctly")
    void shouldHandleDecimalNumbersCorrectly() {
        // Given
        ProductRecord preciseRecord = new ProductRecord("TEST001", "fitness", 29.999, true);
        Filter gteFilter = new Filter("price", Filter.FilterOperator.GTE, "29.99");
        Filter lteFilter = new Filter("price", Filter.FilterOperator.LTE, "30.00");

        // When & Then
        assertTrue(FilterMatcher.matches(preciseRecord, gteFilter)); // 29.999 >= 29.99
        assertTrue(FilterMatcher.matches(preciseRecord, lteFilter)); // 29.999 <= 30.00
    }

    @Test
    @DisplayName("Should handle zero values correctly")
    void shouldHandleZeroValuesCorrectly() {
        // Given
        ProductRecord zeroPriceRecord = new ProductRecord("TEST001", "fitness", 0.0, true);
        Filter zeroFilter = new Filter("price", Filter.FilterOperator.EQUALS, "0");
        Filter gtZeroFilter = new Filter("price", Filter.FilterOperator.GT, "0");

        // When & Then
        assertTrue(FilterMatcher.matches(zeroPriceRecord, zeroFilter));
        assertFalse(FilterMatcher.matches(zeroPriceRecord, gtZeroFilter));
    }

    @Test
    @DisplayName("Should handle negative numbers correctly")
    void shouldHandleNegativeNumbersCorrectly() {
        // Given
        ProductRecord negativePriceRecord = new ProductRecord("TEST001", "fitness", -10.0, true);
        Filter negativeFilter = new Filter("price", Filter.FilterOperator.LT, "0");
        Filter gtNegativeFilter = new Filter("price", Filter.FilterOperator.GT, "-20");

        // When & Then
        assertTrue(FilterMatcher.matches(negativePriceRecord, negativeFilter)); // -10.0 < 0
        assertTrue(FilterMatcher.matches(negativePriceRecord, gtNegativeFilter)); // -10.0 > -20
    }

    @Test
    @DisplayName("Should handle string values with spaces")
    void shouldHandleStringValuesWithSpaces() {
        // Given
        ProductRecord recordWithSpaces = new ProductRecord("TEST001", "fitness equipment", 29.99, true);
        Filter spaceFilter = new Filter("category", Filter.FilterOperator.EQUALS, "fitness equipment");

        // When & Then
        assertTrue(FilterMatcher.matches(recordWithSpaces, spaceFilter));
    }

    @Test
    @DisplayName("Should handle edge case numeric comparisons")
    void shouldHandleEdgeCaseNumericComparisons() {
        // Given
        ProductRecord edgeRecord = new ProductRecord("TEST001", "fitness", 29.99, true);
        
        // Test boundary conditions
        Filter exactMatch = new Filter("price", Filter.FilterOperator.GTE, "29.99");
        Filter justAbove = new Filter("price", Filter.FilterOperator.GT, "29.98");
        Filter justBelow = new Filter("price", Filter.FilterOperator.LT, "30.00");

        // When & Then
        assertTrue(FilterMatcher.matches(edgeRecord, exactMatch)); // 29.99 >= 29.99
        assertTrue(FilterMatcher.matches(edgeRecord, justAbove));  // 29.99 > 29.98
        assertTrue(FilterMatcher.matches(edgeRecord, justBelow));  // 29.99 < 30.00
    }
}
