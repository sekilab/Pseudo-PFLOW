package truck.sim.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Unit tests for CSVLoader utility class.
 */
class CSVLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Load valid CSV file")
    void testLoadValidCSV() throws IOException {
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n" +
            "Bob,25,Osaka\n" +
            "Charlie,35,Kyoto\n"
        );

        List<Person> people = CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            String[] parts = line.split(",");
            return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
        });

        assertEquals(3, people.size());
        assertEquals("Alice", people.get(0).name);
        assertEquals(30, people.get(0).age);
        assertEquals("Tokyo", people.get(0).city);
    }

    @Test
    @DisplayName("Handle empty CSV file")
    void testEmptyCSV() {
        Path csvFile = tempDir.resolve("empty.csv");

        assertThrows(IOException.class, () -> {
            CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> null);
        });
    }

    @Test
    @DisplayName("Handle CSV with only header")
    void testOnlyHeader() throws IOException {
        Path csvFile = tempDir.resolve("header-only.csv");
        Files.writeString(csvFile, "name,age,city\n");

        List<Person> people = CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            String[] parts = line.split(",");
            return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
        });

        assertEquals(0, people.size());
    }

    @Test
    @DisplayName("Skip malformed rows gracefully")
    void testMalformedRows() throws IOException {
        Path csvFile = tempDir.resolve("malformed.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n" +
            "Bob,INVALID,Osaka\n" +  // Invalid age
            "Charlie,35,Kyoto\n"
        );

        List<Person> people = CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            String[] parts = line.split(",");
            return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
        });

        // Should skip the malformed row
        assertEquals(2, people.size());
        assertEquals("Alice", people.get(0).name);
        assertEquals("Charlie", people.get(1).name);
    }

    @Test
    @DisplayName("Skip empty lines")
    void testEmptyLines() throws IOException {
        Path csvFile = tempDir.resolve("empty-lines.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n" +
            "\n" +  // Empty line
            "Bob,25,Osaka\n" +
            "   \n" +  // Whitespace line
            "Charlie,35,Kyoto\n"
        );

        List<Person> people = CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            String[] parts = line.split(",");
            return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
        });

        assertEquals(3, people.size());
    }

    @Test
    @DisplayName("Load with header validation")
    void testLoadWithHeaderValidation() throws IOException {
        Path csvFile = tempDir.resolve("validated.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n"
        );

        List<Person> people = CSVLoader.loadCSVWithHeader(
            csvFile.toString(),
            "name,age,city",
            (line, lineNum) -> {
                String[] parts = line.split(",");
                return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
            }
        );

        assertEquals(1, people.size());
    }

    @Test
    @DisplayName("Header validation fails on mismatch")
    void testHeaderValidationFails() throws IOException {
        Path csvFile = tempDir.resolve("wrong-header.csv");
        Files.writeString(csvFile,
            "name,age,country\n" +  // Wrong header
            "Alice,30,Japan\n"
        );

        assertThrows(IOException.class, () -> {
            CSVLoader.loadCSVWithHeader(
                csvFile.toString(),
                "name,age,city",  // Expected header
                (line, lineNum) -> null
            );
        });
    }

    @Test
    @DisplayName("Parser can return null to skip rows")
    void testNullSkipping() throws IOException {
        Path csvFile = tempDir.resolve("skip-rows.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n" +
            "Bob,25,Osaka\n" +
            "Charlie,35,Kyoto\n"
        );

        List<Person> people = CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            String[] parts = line.split(",");
            // Skip people under 30
            if (Integer.parseInt(parts[1]) < 30) return null;
            return new Person(parts[0], Integer.parseInt(parts[1]), parts[2]);
        });

        assertEquals(2, people.size());  // Alice and Charlie only
        assertTrue(people.stream().allMatch(p -> p.age >= 30));
    }

    @Test
    @DisplayName("Line numbers passed to parser")
    void testLineNumbers() throws IOException {
        Path csvFile = tempDir.resolve("line-nums.csv");
        Files.writeString(csvFile,
            "name,age,city\n" +
            "Alice,30,Tokyo\n" +
            "Bob,25,Osaka\n"
        );

        CSVLoader.loadCSV(csvFile.toString(), (line, lineNum) -> {
            // Line 2 should be Alice, line 3 should be Bob
            assertTrue(lineNum >= 2 && lineNum <= 3);
            return null;
        });
    }

    @Test
    @DisplayName("File not found throws IOException")
    void testFileNotFound() {
        assertThrows(IOException.class, () -> {
            CSVLoader.loadCSV("nonexistent.csv", (line, lineNum) -> null);
        });
    }

    // Helper class for testing
    static class Person {
        final String name;
        final int age;
        final String city;

        Person(String name, int age, String city) {
            this.name = name;
            this.age = age;
            this.city = city;
        }
    }
}
