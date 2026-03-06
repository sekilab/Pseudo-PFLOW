package truck.sim.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading and parsing CSV files.
 *
 * <p>Provides a generic, reusable CSV loading mechanism that handles:
 * <ul>
 *   <li>Header row skipping</li>
 *   <li>Line-by-line parsing with custom parser</li>
 *   <li>Error handling and logging</li>
 *   <li>Graceful handling of malformed rows</li>
 * </ul>
 */
public final class CSVLoader {

    // Prevent instantiation
    private CSVLoader() {}

    /**
     * Load CSV file and parse each row using the provided parser.
     *
     * <p>The first line is assumed to be a header and is skipped.
     * Each subsequent line is parsed using the provided parser.
     * Parsing errors for individual rows are logged but do not stop processing.
     *
     * @param <T> The type of objects to create from CSV rows
     * @param filePath Path to the CSV file
     * @param parser Parser that converts CSV line to object
     * @return List of successfully parsed objects
     * @throws IOException If file cannot be read
     *
     * @see CSVRowParser
     */
    public static <T> List<T> loadCSV(
            String filePath,
            CSVRowParser<T> parser) throws IOException {

        List<T> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            // Read and skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file: " + filePath);
            }

            String line;
            int lineNumber = 1;

            // Process each data line
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    T object = parser.parse(line, lineNumber);
                    if (object != null) {
                        results.add(object);
                    }
                } catch (Exception e) {
                    // Log warning but continue processing
                    System.err.println("[WARN] Skipping line " + lineNumber +
                                     " in " + filePath + ": " + e.getMessage());
                }
            }
        }

        return results;
    }

    /**
     * Load CSV file with a known header and validate it.
     *
     * <p>This variant allows checking that the CSV has the expected header
     * before parsing data rows.
     *
     * @param <T> The type of objects to create from CSV rows
     * @param filePath Path to the CSV file
     * @param expectedHeader Expected header line (exact match)
     * @param parser Parser that converts CSV line to object
     * @return List of successfully parsed objects
     * @throws IOException If file cannot be read or header doesn't match
     */
    public static <T> List<T> loadCSVWithHeader(
            String filePath,
            String expectedHeader,
            CSVRowParser<T> parser) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new IOException("Empty CSV file: " + filePath);
            }

            if (!headerLine.trim().equals(expectedHeader.trim())) {
                throw new IOException("Header mismatch in " + filePath +
                                    "\nExpected: " + expectedHeader +
                                    "\nActual: " + headerLine);
            }

            List<T> results = new ArrayList<>();
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    T object = parser.parse(line, lineNumber);
                    if (object != null) {
                        results.add(object);
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Skipping line " + lineNumber +
                                     " in " + filePath + ": " + e.getMessage());
                }
            }

            return results;
        }
    }

    /**
     * Functional interface for parsing a single CSV row.
     *
     * <p>Implementations should parse the CSV line and return an object,
     * or return null to skip the line, or throw an exception on error.
     *
     * @param <T> The type of object to create from the CSV row
     */
    @FunctionalInterface
    public interface CSVRowParser<T> {
        /**
         * Parse a CSV line into an object.
         *
         * @param line The CSV line (comma-separated values)
         * @param lineNumber Line number in file (for error reporting)
         * @return Parsed object, or null to skip this line
         * @throws Exception On parsing error (will be caught and logged)
         */
        T parse(String line, int lineNumber) throws Exception;
    }
}
