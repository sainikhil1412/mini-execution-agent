package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.ProductRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvService Tests")
class CsvServiceTest {

    private CsvService csvService;

    @TempDir
    Path tempDir;

    private String testCsvPath;
    private String outputCsvPath;

    @BeforeEach
    void setUp() throws IOException {
        csvService = new CsvService();
        testCsvPath = tempDir.resolve("test_input.csv").toString();
        outputCsvPath = tempDir.resolve("test_output.csv").toString();

        // Create test CSV file
        try (FileWriter writer = new FileWriter(testCsvPath)) {
            writer.write("sku,category,price,in_stock\n");
            writer.write("TEST001,fitness,29.99,true\n");
            writer.write("TEST002,yoga,19.99,false\n");
            writer.write("TEST003,accessories,9.99,true\n");
            writer.write("TEST004,fitness,39.99,true\n");
        }
    }

    @Test
    @DisplayName("Should read CSV successfully")
    void shouldReadCsvSuccessfully() {
        // When
        List<ProductRecord> records = csvService.readCsv(testCsvPath);

        // Then
        assertNotNull(records);
        assertEquals(4, records.size());

        ProductRecord record1 = records.get(0);
        assertEquals("TEST001", record1.getSku());
        assertEquals("fitness", record1.getCategory());
        assertEquals(29.99, record1.getPrice());
        assertTrue(record1.isInStock());

        ProductRecord record2 = records.get(1);
        assertEquals("TEST002", record2.getSku());
        assertEquals("yoga", record2.getCategory());
        assertEquals(19.99, record2.getPrice());
        assertFalse(record2.isInStock());
    }

    @Test
    @DisplayName("Should throw exception for non-existent file")
    void shouldThrowExceptionForNonExistentFile() {
        // Given
        String nonExistentPath = tempDir.resolve("non_existent.csv").toString();

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            csvService.readCsv(nonExistentPath);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to read CSV"));
    }

    @Test
    @DisplayName("Should throw exception for empty CSV file")
    void shouldThrowExceptionForEmptyCsvFile() throws IOException {
        // Given
        String emptyCsvPath = tempDir.resolve("empty.csv").toString();
        new File(emptyCsvPath).createNewFile();

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            csvService.readCsv(emptyCsvPath);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("CSV file is empty"));
    }

    @Test
    @DisplayName("Should handle CSV with malformed lines")
    void shouldHandleCsvWithMalformedLines() throws IOException {
        // Given
        String malformedCsvPath = tempDir.resolve("malformed.csv").toString();
        try (FileWriter writer = new FileWriter(malformedCsvPath)) {
            writer.write("sku,category,price,in_stock\n");
            writer.write("TEST001,fitness,29.99,true\n");
            writer.write("INVALID_LINE_WITH_ONLY_TWO_FIELDS\n");
            writer.write("TEST003,accessories,9.99,true\n");
        }

        // When
        List<ProductRecord> records = csvService.readCsv(malformedCsvPath);

        // Then
        assertEquals(2, records.size()); // Should skip malformed line
        assertEquals("TEST001", records.get(0).getSku());
        assertEquals("TEST003", records.get(1).getSku());
    }

    @Test
    @DisplayName("Should handle CSV with invalid price format")
    void shouldHandleCsvWithInvalidPriceFormat() throws IOException {
        // Given
        String invalidPriceCsvPath = tempDir.resolve("invalid_price.csv").toString();
        try (FileWriter writer = new FileWriter(invalidPriceCsvPath)) {
            writer.write("sku,category,price,in_stock\n");
            writer.write("TEST001,fitness,29.99,true\n");
            writer.write("TEST002,yoga,invalid_price,true\n");
            writer.write("TEST003,accessories,9.99,true\n");
        }

        // When
        List<ProductRecord> records = csvService.readCsv(invalidPriceCsvPath);

        // Then
        assertEquals(2, records.size()); // Should skip line with invalid price
        assertEquals("TEST001", records.get(0).getSku());
        assertEquals("TEST003", records.get(1).getSku());
    }

    @Test
    @DisplayName("Should write CSV successfully")
    void shouldWriteCsvSuccessfully() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true),
                new ProductRecord("TEST002", "yoga", 19.99, false)
        );

        // When
        csvService.writeCsv(records, outputCsvPath);

        // Then
        File outputFile = new File(outputCsvPath);
        assertTrue(outputFile.exists());

        // Verify content by reading it back
        List<ProductRecord> writtenRecords = csvService.readCsv(outputCsvPath);
        assertEquals(2, writtenRecords.size());
        assertEquals("TEST001", writtenRecords.get(0).getSku());
        assertEquals("TEST002", writtenRecords.get(1).getSku());
    }

    @Test
    @DisplayName("Should create parent directories when writing CSV")
    void shouldCreateParentDirectoriesWhenWritingCsv() {
        // Given
        String nestedPath = tempDir.resolve("nested").resolve("dir").resolve("output.csv").toString();
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true)
        );

        // When
        csvService.writeCsv(records, nestedPath);

        // Then
        File outputFile = new File(nestedPath);
        assertTrue(outputFile.exists());
        assertTrue(outputFile.getParentFile().exists());
    }

    @Test
    @DisplayName("Should write CSV atomically successfully")
    void shouldWriteCsvAtomicallySuccessfully() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true),
                new ProductRecord("TEST002", "yoga", 19.99, false)
        );

        // When
        csvService.writeCsvAtomic(records, outputCsvPath);

        // Then
        File outputFile = new File(outputCsvPath);
        assertTrue(outputFile.exists());

        // Verify content by reading it back
        List<ProductRecord> writtenRecords = csvService.readCsv(outputCsvPath);
        assertEquals(2, writtenRecords.size());
        assertEquals("TEST001", writtenRecords.get(0).getSku());
        assertEquals("TEST002", writtenRecords.get(1).getSku());
    }

    @Test
    @DisplayName("Should handle empty records list when writing CSV")
    void shouldHandleEmptyRecordsListWhenWritingCsv() {
        // Given
        List<ProductRecord> emptyRecords = List.of();

        // When
        csvService.writeCsv(emptyRecords, outputCsvPath);

        // Then
        File outputFile = new File(outputCsvPath);
        assertTrue(outputFile.exists());

        // Should only have header
        List<ProductRecord> writtenRecords = csvService.readCsv(outputCsvPath);
        assertEquals(0, writtenRecords.size());
    }

    @Test
    @DisplayName("Should format price with 2 decimal places when writing CSV")
    void shouldFormatPriceWith2DecimalPlacesWhenWritingCsv() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.999, true),
                new ProductRecord("TEST002", "yoga", 19.9, false)
        );

        // When
        csvService.writeCsv(records, outputCsvPath);

        // Then
        List<ProductRecord> writtenRecords = csvService.readCsv(outputCsvPath);
        assertEquals(30.0, writtenRecords.get(0).getPrice()); // Should be rounded to 30.00
        assertEquals(19.9, writtenRecords.get(1).getPrice());   // Should be 19.90
    }

    @Test
    @DisplayName("Should throw exception when writing to invalid path")
    void shouldThrowExceptionWhenWritingToInvalidPath() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true)
        );
        String invalidPath = "C:\\invalid\\path\\that\\does\\not\\exist\\output.csv";

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            csvService.writeCsv(records, invalidPath);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to write CSV"));
    }

    @Test
    @DisplayName("Should throw exception when writing atomically to invalid path")
    void shouldThrowExceptionWhenWritingAtomicallyToInvalidPath() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true)
        );
        String invalidPath = "C:\\invalid\\path\\that\\does\\not\\exist\\output.csv";

        // When & Then
        AgentException exception = assertThrows(AgentException.class, () -> {
            csvService.writeCsvAtomic(records, invalidPath);
        });

        assertEquals("CSV_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to write CSV atomically"));
    }

    @Test
    @DisplayName("Should handle boolean values correctly")
    void shouldHandleBooleanValuesCorrectly() {
        // Given
        List<ProductRecord> records = List.of(
                new ProductRecord("TEST001", "fitness", 29.99, true),
                new ProductRecord("TEST002", "yoga", 19.99, false)
        );

        // When
        csvService.writeCsv(records, outputCsvPath);

        // Then
        List<ProductRecord> writtenRecords = csvService.readCsv(outputCsvPath);
        assertTrue(writtenRecords.get(0).isInStock());
        assertFalse(writtenRecords.get(1).isInStock());
    }

    @Test
    @DisplayName("Should preserve string values with spaces")
    void shouldPreserveStringValuesWithSpaces() throws IOException {
        // Given
        String csvWithSpacesPath = tempDir.resolve("with_spaces.csv").toString();
        try (FileWriter writer = new FileWriter(csvWithSpacesPath)) {
            writer.write("sku,category,price,in_stock\n");
            writer.write("TEST001,fitness equipment,29.99,true\n");
            writer.write("TEST002,yoga mat,19.99,false\n");
        }

        // When
        List<ProductRecord> records = csvService.readCsv(csvWithSpacesPath);

        // Then
        assertEquals(2, records.size());
        assertEquals("fitness equipment", records.get(0).getCategory());
        assertEquals("yoga mat", records.get(1).getCategory());
    }
}
