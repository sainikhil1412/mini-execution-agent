package com.agent.service;

import com.agent.exception.AgentException;
import com.agent.model.ProductRecord;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CsvService {

    private static final String[] CSV_HEADER = {"sku", "category", "price", "in_stock"};

    public List<ProductRecord> readCsv(String filePath) {
        log.debug("Reading CSV from path: {}", filePath);
        List<ProductRecord> records = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] header = reader.readNext(); // skip header
            if (header == null) {
                log.error("CSV file is empty: {}", filePath);
                throw new AgentException("CSV_ERROR", "CSV file is empty: " + filePath);
            }

            String[] line;
            int lineNumber = 1; // header is line 1
            while ((line = reader.readNext()) != null) {
                lineNumber++;
                if (line.length < 4) {
                    log.warn("Skipping line {} - insufficient columns: {}", lineNumber, line.length);
                    continue;
                }
                try {
                    ProductRecord record = new ProductRecord(
                            line[0].trim(),
                            line[1].trim(),
                            Double.parseDouble(line[2].trim()),
                            Boolean.parseBoolean(line[3].trim())
                    );
                    records.add(record);
                } catch (NumberFormatException e) {
                    log.warn("Skipping line {} - invalid price format: '{}'", lineNumber, line[2].trim());
                }
            }
            log.info("Successfully read {} records from CSV: {}", records.size(), filePath);
        } catch (IOException | CsvValidationException e) {
            log.error("Failed to read CSV file: {}", filePath, e);
            throw new AgentException("CSV_ERROR", "Failed to read CSV: " + e.getMessage());
        }
        return records;
    }

    public void writeCsv(List<ProductRecord> records, String filePath) {
        log.debug("Writing {} records to CSV: {}", records.size(), filePath);
        // Ensure parent directory exists
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            writer.writeNext(CSV_HEADER);
            for (ProductRecord r : records) {
                writer.writeNext(new String[]{
                        r.getSku(),
                        r.getCategory(),
                        String.format("%.2f", r.getPrice()),
                        String.valueOf(r.isInStock())
                });
            }
            log.info("Successfully wrote {} records to CSV: {}", records.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to write CSV file: {}", filePath, e);
            throw new AgentException("CSV_ERROR", "Failed to write CSV: " + e.getMessage());
        }
    }

    public void writeCsvAtomic(List<ProductRecord> records, String filePath) {
        log.debug("Writing {} records atomically to CSV: {}", records.size(), filePath);
        Path target = Path.of(filePath);
        Path parent = target.getParent() == null ? Path.of(".") : target.getParent();

        try {
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            log.debug("Created temporary file: {}", temp);
            writeCsv(records, temp.toString());

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                log.info("Successfully wrote {} records atomically to CSV: {}", records.size(), filePath);
            } catch (AtomicMoveNotSupportedException e) {
                log.warn("Atomic move not supported, falling back to standard move for: {}", filePath);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Successfully wrote {} records to CSV with standard move: {}", records.size(), filePath);
            }
        } catch (IOException e) {
            log.error("Failed to write CSV atomically: {}", filePath, e);
            throw new AgentException("CSV_ERROR", "Failed to write CSV atomically: " + e.getMessage());
        }
    }
}
