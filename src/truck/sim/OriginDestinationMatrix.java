package truck.sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Origin-Destination Matrix for realistic flow-based routing.
 * Based on Tokyo Metropolitan Freight Survey (H25/2013) File 08.
 *
 * Implements weighted destination selection based on survey O-D flows
 * instead of simple zone attractiveness.
 *
 * @version 1.1
 */
public class OriginDestinationMatrix {

    private final int numZones;
    private final double[][] flowMatrix;  // [origin][destination] = flow weight (normalized to probabilities)
    private double[] rawOutflowTotals;    // Pre-normalization row sums from MFS CSV
    /**
     * Create O-D matrix with specified number of zones.
     */
    public OriginDestinationMatrix(int numZones) {
        this.numZones = numZones;
        this.flowMatrix = new double[numZones][numZones];
        this.rawOutflowTotals = new double[numZones];  // zero until CSV loaded

        // Initialize with default uniform distribution
        initializeDefaultFlows();
    }

    /**
     * Initialize with default flow patterns based on MFS data insights.
     *
     * Key patterns from survey:
     * - Strong intra-zone flows (deliveries within same zone)
     * - Hub-spoke patterns from industrial/port zones
     * - Port areas have unique distribution patterns
     * - Residential zones are primarily destinations
     */
    private void initializeDefaultFlows() {
        for (int origin = 0; origin < numZones; origin++) {
            for (int dest = 0; dest < numZones; dest++) {
                if (origin == dest) {
                    // Strong intra-zone flow (30-40% of total)
                    flowMatrix[origin][dest] = 2.0;
                } else {
                    // Base inter-zone flow
                    flowMatrix[origin][dest] = 1.0;
                }
            }
        }

        // Apply zone-specific adjustments based on MFS patterns
        applyZoneCharacteristics();
    }

    /**
     * Apply zone characteristics based on MFS analysis.
     * Adapts to different zone configurations (8, 16, or 20 zones).
     *
     * Zone patterns based on Tokyo MFS data:
     * - Port/Industrial zones: High outbound flows
     * - Distribution centers: Balanced flows
     * - CBD/Commercial: High inbound flows
     * - Residential: Low outbound, high inbound
     */
    private void applyZoneCharacteristics() {
        // Apply patterns based on number of zones
        if (numZones == 8) {
            // Tokyo Core (8 zones)
            applyTokyoCorePatterns();
        } else if (numZones >= 16 && numZones <= 20) {
            // Tokyo Metro or Greater Tokyo
            applyTokyoMetroPatterns();
        } else {
            // Generic patterns for other configurations
            applyGenericPatterns();
        }
    }

    /**
     * Apply flow patterns for Tokyo Core (8 zones).
     * DZ01: Central CBD, DZ02: Southern Port, DZ03: Northwest,
     * DZ04: Eastern Industrial, DZ05: Western Residential,
     * DZ06: Northern, DZ07: Northeast, DZ08: West Suburbs
     */
    private void applyTokyoCorePatterns() {
        // Port (DZ02=1) → Industrial/Distribution (DZ04=3, DZ07=6, DZ08=7)
        int port = 1;
        int[] industrial = {3, 6, 7};
        for (int ind : industrial) {
            flowMatrix[port][ind] *= 2.5;
        }

        // Industrial (DZ04=3, DZ07=6) → Distribution (DZ08=7)
        flowMatrix[3][7] *= 1.8;
        flowMatrix[6][7] *= 1.8;

        // Distribution (DZ08=7) → CBD/Residential (DZ01=0, DZ05=4, DZ06=5)
        flowMatrix[7][0] *= 1.5;  // To CBD
        flowMatrix[7][4] *= 1.3;  // To Western Residential
        flowMatrix[7][5] *= 1.3;  // To Northern

        // Residential zones (DZ05=4, DZ06=5) have low outbound
        for (int dest = 0; dest < numZones; dest++) {
            if (dest != 4) flowMatrix[4][dest] *= 0.5;
            if (dest != 5) flowMatrix[5][dest] *= 0.5;
        }
    }

    /**
     * Apply flow patterns for Tokyo Metro (20 zones) or Greater Tokyo (16 zones).
     */
    private void applyTokyoMetroPatterns() {
        // Port/Cargo hubs → Warehouses
        int[] portZones = safeIndices(new int[]{1, 8, 9, 12, 13}, numZones);
        int[] warehouseZones = safeIndices(new int[]{3, 6, 7, 10}, numZones);

        for (int port : portZones) {
            for (int warehouse : warehouseZones) {
                flowMatrix[port][warehouse] *= 2.5;
            }
        }

        // Industrial zones → Distribution centers
        int[] industrialZones = safeIndices(new int[]{3, 6, 10}, numZones);
        int[] distributionZones = safeIndices(new int[]{7, 12, 13}, numZones);

        for (int industrial : industrialZones) {
            for (int dist : distributionZones) {
                flowMatrix[industrial][dist] *= 1.8;
            }
        }

        // Distribution → Retail/Residential
        int[] retailZones = safeIndices(new int[]{0, 2, 14, 15, 16, 17, 18}, numZones);
        int[] residentialZones = safeIndices(new int[]{4, 5, 11}, numZones);

        for (int dist : distributionZones) {
            for (int retail : retailZones) {
                flowMatrix[dist][retail] *= 1.5;
            }
            for (int residential : residentialZones) {
                flowMatrix[dist][residential] *= 1.3;
            }
        }

        // Residential zones have low outbound
        for (int residential : residentialZones) {
            for (int dest = 0; dest < numZones; dest++) {
                if (dest != residential) {
                    flowMatrix[residential][dest] *= 0.5;
                }
            }
        }
    }

    /**
     * Apply generic hub-spoke patterns for other configurations.
     */
    private void applyGenericPatterns() {
        // First 20% of zones are hubs (high outbound)
        // Middle 50% are distribution
        // Last 30% are destinations (high inbound)
        int hubCount = Math.max(1, numZones / 5);
        int destStart = numZones - Math.max(1, (numZones * 3) / 10);

        for (int hub = 0; hub < hubCount; hub++) {
            for (int dest = hubCount; dest < numZones; dest++) {
                flowMatrix[hub][dest] *= 1.5;
            }
        }

        for (int dest = destStart; dest < numZones; dest++) {
            for (int origin = 0; origin < destStart; origin++) {
                if (origin != dest) {
                    flowMatrix[dest][origin] *= 0.5;
                }
            }
        }
    }

    /**
     * Filter indices to only include valid ones (< numZones).
     */
    private int[] safeIndices(int[] indices, int maxIndex) {
        int count = 0;
        for (int idx : indices) {
            if (idx < maxIndex) count++;
        }

        int[] result = new int[count];
        int pos = 0;
        for (int idx : indices) {
            if (idx < maxIndex) result[pos++] = idx;
        }
        return result;
    }

    /**
     * Set flow weight between origin and destination.
     */
    public void setFlow(int origin, int dest, double weight) {
        if (origin >= 0 && origin < numZones && dest >= 0 && dest < numZones) {
            flowMatrix[origin][dest] = weight;
        }
    }

    /**
     * Load O-D matrix from MFS CSV file and normalize to probabilities.
     *
     * Phase 3: Model Refinement - O-D Matrix Implementation
     *
     * @param csvPath Path to O-D matrix CSV (e.g., od_matrix_regional_volume.csv)
     * @param zoneMapping Map from MFS zone name to simulation zone index
     * @throws IOException if file read fails
     */
    public void loadFromMFSCSV(String csvPath, Map<String, Integer> zoneMapping)
            throws IOException {

        System.out.println("[O-D] Loading MFS O-D matrix from: " + csvPath);

        // Clear existing flows
        for (int i = 0; i < numZones; i++) {
            for (int j = 0; j < numZones; j++) {
                flowMatrix[i][j] = 0.0;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            // Read header to get destination zone names
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file");
            }

            String[] headers = headerLine.split(",");

            // Build destination zone name -> column index mapping
            // Skip first 2 columns (large_zone, region)
            List<String> destZoneNames = new ArrayList<>();
            for (int i = 2; i < headers.length; i++) {
                String zoneName = headers[i].trim();
                // Skip aggregate columns
                if (!zoneName.equals("all_regions") &&
                    !zoneName.equals("unknown") &&
                    !zoneName.equals("overseas")) {
                    destZoneNames.add(zoneName);
                } else {
                    destZoneNames.add(null);  // Mark for skipping
                }
            }

            System.out.println("[O-D] Found " + destZoneNames.size() + " destination columns");

            // Process data rows
            String line;
            int rowCount = 0;
            int mappedCount = 0;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // Stop at section boundary — Section 1 is total tons/day from MFS File 08
                // (od_volume.csv is extracted from the tons sheet; veh/day lives in
                // od_volume_expanded.csv). Sections 2-22 are per-commodity tonnage breakdowns
                // that we must not mix into the total, so we stop at the first section marker.
                if (line.startsWith("\u25CB") || line.startsWith("○")) {
                    System.out.println("[O-D] Reached section boundary at row " + rowCount +
                        ", stopping (Section 1 = tons/day only)");
                    break;
                }

                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                // Get origin zone name (column 2)
                String originZoneName = parts[1].trim();

                // Map to simulation zone index
                Integer originIndex = zoneMapping.get(originZoneName);
                if (originIndex == null || originIndex < 0 || originIndex >= numZones) {
                    // Skip unmapped zones (external zones)
                    continue;
                }

                // Parse flows for each destination
                for (int i = 0; i < destZoneNames.size() && (i + 2) < parts.length; i++) {
                    String destZoneName = destZoneNames.get(i);
                    if (destZoneName == null) continue;  // Skip aggregate columns

                    // Map destination to simulation zone index
                    Integer destIndex = zoneMapping.get(destZoneName);
                    if (destIndex == null || destIndex < 0 || destIndex >= numZones) {
                        // Skip unmapped zones
                        continue;
                    }

                    // Parse flow value
                    String flowStr = parts[i + 2].trim();
                    if (flowStr.isEmpty()) continue;

                    try {
                        double flowValue = Double.parseDouble(flowStr);

                        // Aggregate flows for zones mapped to same sim zone
                        flowMatrix[originIndex][destIndex] += flowValue;

                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }

                rowCount++;
                if (originIndex != null) mappedCount++;
            }

            System.out.println("[O-D] Processed " + rowCount + " MFS origin zones");
            System.out.println("[O-D] Mapped " + mappedCount + " zones to simulation zones");

            // Normalize flows to probabilities
            normalizeFlowsToProbabilities();

            // Validate matrix
            validateMatrix();

            System.out.println("[O-D] Successfully loaded and normalized O-D matrix");
        }
    }

    /**
     * Normalize flow matrix to probability matrix (rows sum to 1.0).
     * Captures raw row totals in rawOutflowTotals before normalization
     * so callers can weight zone assignment by actual MFS survey volumes.
     *
     * Each row represents an origin zone's probability distribution
     * over all possible destination zones.
     */
    private void normalizeFlowsToProbabilities() {
        System.out.println("[O-D] Normalizing flows to probabilities...");

        int zeroFlowOrigins = 0;
        rawOutflowTotals = new double[numZones];

        for (int origin = 0; origin < numZones; origin++) {
            // Calculate total outbound flow from this origin
            double totalFlow = 0.0;
            for (int dest = 0; dest < numZones; dest++) {
                totalFlow += flowMatrix[origin][dest];
            }

            // Save raw total before normalization
            rawOutflowTotals[origin] = totalFlow;

            // Normalize to probabilities
            if (totalFlow > 0.0) {
                for (int dest = 0; dest < numZones; dest++) {
                    flowMatrix[origin][dest] /= totalFlow;
                }
            } else {
                // No outbound flows - use uniform distribution as fallback
                zeroFlowOrigins++;
                for (int dest = 0; dest < numZones; dest++) {
                    flowMatrix[origin][dest] = 1.0 / numZones;
                }
            }
        }

        if (zeroFlowOrigins > 0) {
            System.out.println("[O-D] Warning: " + zeroFlowOrigins +
                " zones had no outbound flows (using uniform distribution)");
        }

        // Log outflow summary for diagnostic purposes
        logOutflowSummary();
    }

    /**
     * Log top/bottom zones by raw outflow total for diagnostics.
     */
    private void logOutflowSummary() {
        double totalFlow = 0;
        int maxIdx = 0, minIdx = 0;
        for (int i = 0; i < numZones; i++) {
            totalFlow += rawOutflowTotals[i];
            if (rawOutflowTotals[i] > rawOutflowTotals[maxIdx]) maxIdx = i;
            if (rawOutflowTotals[i] < rawOutflowTotals[minIdx]) minIdx = i;
        }

        // Sort indices by outflow for top/bottom 5
        Integer[] indices = new Integer[numZones];
        for (int i = 0; i < numZones; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(rawOutflowTotals[b], rawOutflowTotals[a]));

        StringBuilder top5 = new StringBuilder("[O-D] Top 5 outflow zones: ");
        StringBuilder bot5 = new StringBuilder("[O-D] Bottom 5 outflow zones: ");
        for (int i = 0; i < Math.min(5, numZones); i++) {
            if (i > 0) { top5.append(", "); bot5.append(", "); }
            top5.append("zone").append(indices[i]).append("=").append(String.format("%.0f", rawOutflowTotals[indices[i]]));
            int botIdx = numZones - 1 - i;
            bot5.append("zone").append(indices[botIdx]).append("=").append(String.format("%.0f", rawOutflowTotals[indices[botIdx]]));
        }
        System.out.println(top5);
        System.out.println(bot5);
        System.out.println("[O-D] Total raw outflow across all zones: " + String.format("%.0f", totalFlow));
    }

    /**
     * Validate O-D matrix structure.
     *
     * Checks:
     * - All rows sum to 1.0 (probability constraint)
     * - No negative values
     * - No NaN or Infinity values
     */
    private void validateMatrix() {
        boolean valid = true;
        int errorCount = 0;

        for (int origin = 0; origin < numZones; origin++) {
            double rowSum = 0.0;

            for (int dest = 0; dest < numZones; dest++) {
                double prob = flowMatrix[origin][dest];

                // Check for invalid values
                if (Double.isNaN(prob) || Double.isInfinite(prob)) {
                    System.err.println("[O-D] ERROR: Invalid value at [" + origin + "][" + dest + "]: " + prob);
                    valid = false;
                    errorCount++;
                }

                if (prob < 0.0) {
                    System.err.println("[O-D] ERROR: Negative probability at [" + origin + "][" + dest + "]: " + prob);
                    valid = false;
                    errorCount++;
                }

                rowSum += prob;
            }

            // Check row sum (should be 1.0, allow small floating point error)
            if (Math.abs(rowSum - 1.0) > 0.001) {
                System.err.println("[O-D] ERROR: Row " + origin + " sum = " + rowSum + " (expected 1.0)");
                valid = false;
                errorCount++;
            }

            if (errorCount > 10) {
                System.err.println("[O-D] Too many errors, stopping validation");
                break;
            }
        }

        if (valid) {
            System.out.println("[O-D] Matrix validation passed");
        } else {
            System.err.println("[O-D] Matrix validation FAILED (" + errorCount + " errors)");
        }
    }

    /**
     * Get flow weight from origin to destination.
     */
    public double getFlow(int origin, int dest) {
        if (origin >= 0 && origin < numZones && dest >= 0 && dest < numZones) {
            return flowMatrix[origin][dest];
        }
        return 0.0;
    }

    /**
     * Select destination based on flow probabilities from given origin.
     *
     * @param originZoneIndex 0-based zone index
     * @return destination zone index
     */
    public int selectDestination(int originZoneIndex) {
        if (originZoneIndex < 0 || originZoneIndex >= numZones) {
            // Invalid origin, return random
            return ThreadLocalRandom.current().nextInt(numZones);
        }

        // Calculate total weight for normalization
        double totalWeight = 0.0;
        for (int dest = 0; dest < numZones; dest++) {
            totalWeight += flowMatrix[originZoneIndex][dest];
        }

        if (totalWeight <= 0.0) {
            // No flows defined, return random
            return ThreadLocalRandom.current().nextInt(numZones);
        }

        // Select destination using weighted random selection
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (int dest = 0; dest < numZones; dest++) {
            cumulative += flowMatrix[originZoneIndex][dest];
            if (roll < cumulative) {
                return dest;
            }
        }

        // Fallback (shouldn't reach here due to numerical precision)
        return numZones - 1;
    }

    /**
     * Get total outbound flow from origin zone.
     */
    public double getTotalOutboundFlow(int originZoneIndex) {
        if (originZoneIndex < 0 || originZoneIndex >= numZones) {
            return 0.0;
        }

        double total = 0.0;
        for (int dest = 0; dest < numZones; dest++) {
            total += flowMatrix[originZoneIndex][dest];
        }
        return total;
    }

    /**
     * Get total inbound flow to destination zone.
     */
    public double getTotalInboundFlow(int destZoneIndex) {
        if (destZoneIndex < 0 || destZoneIndex >= numZones) {
            return 0.0;
        }

        double total = 0.0;
        for (int origin = 0; origin < numZones; origin++) {
            total += flowMatrix[origin][destZoneIndex];
        }
        return total;
    }

    public int getNumZones() {
        return numZones;
    }

    /**
     * Get total raw outbound flow for a zone as loaded from MFS CSV (before normalization).
     * Returns 0 if CSV was not loaded or zone index is invalid.
     * Use this for O-D-weighted truck home zone assignment.
     *
     * @param originZoneIndex 0-based zone index
     * @return Raw MFS survey truck count for this zone as origin
     */
    public double getRawOutflowTotal(int originZoneIndex) {
        if (rawOutflowTotals == null || originZoneIndex < 0 || originZoneIndex >= numZones) {
            return 0.0;
        }
        return rawOutflowTotals[originZoneIndex];
    }
}
