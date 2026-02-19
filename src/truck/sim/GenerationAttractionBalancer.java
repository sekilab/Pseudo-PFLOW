package truck.sim;

import java.io.*;
import java.util.*;

/**
 * Enforces generation-attraction balance based on MFS survey data (File 21).
 * Tracks trip generation and attraction by zone to maintain flow conservation.
 *
 * This implements capacity-based trip generation where:
 * - Zones have target generation/attraction counts from survey data
 * - Trip generation stops when zone reaches generation capacity
 * - Destination selection is weighted by remaining attraction capacity
 * - Mass balance is enforced: total generation ≈ total attraction
 */
public class GenerationAttractionBalancer {

    // Zone capacity targets (from MFS File 21)
    private Map<String, ZoneCapacity> zoneCapacities;

    // Running counts during simulation
    private Map<String, Integer> currentGeneration;
    private Map<String, Integer> currentAttraction;

    private Random random;

    /**
     * Zone capacity data structure.
     */
    private static class ZoneCapacity {
        String zoneId;
        int targetGeneration;    // trips/day to generate
        int targetAttraction;    // trips/day to attract
        double generatedTons;    // tons/day generated
        double attractedTons;    // tons/day attracted
        String facilityType;     // primary facility type

        ZoneCapacity(String zoneId, int targetGen, int targetAtt,
                     double genTons, double attTons, String facility) {
            this.zoneId = zoneId;
            this.targetGeneration = targetGen;
            this.targetAttraction = targetAtt;
            this.generatedTons = genTons;
            this.attractedTons = attTons;
            this.facilityType = facility;
        }
    }

    public GenerationAttractionBalancer(Random random) {
        this.random = random;
        this.zoneCapacities = new HashMap<>();
        this.currentGeneration = new HashMap<>();
        this.currentAttraction = new HashMap<>();
    }

    /**
     * Load G-A targets from CSV file.
     *
     * CSV format:
     * zone_id,generated_tons,generated_trucks,attracted_tons,attracted_trucks,facility_type
     * DZ01,125000,8500,145000,9200,LOGISTICS_HUB
     *
     * @param csvPath Path to generation_attraction_targets.csv
     */
    public void loadTargets(String csvPath) throws IOException {
        System.out.println("[G-A Balance] Loading generation-attraction targets from: " + csvPath);

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                String zoneId = parts[0].trim();
                double genTons = Double.parseDouble(parts[1].trim());
                int genTrucks = Integer.parseInt(parts[2].trim());
                double attTons = Double.parseDouble(parts[3].trim());
                int attTrucks = Integer.parseInt(parts[4].trim());
                String facility = parts[5].trim();

                ZoneCapacity capacity = new ZoneCapacity(
                    zoneId, genTrucks, attTrucks, genTons, attTons, facility);

                zoneCapacities.put(zoneId, capacity);
                currentGeneration.put(zoneId, 0);
                currentAttraction.put(zoneId, 0);
            }
        }

        System.out.println("[G-A Balance] Loaded targets for " + zoneCapacities.size() + " zones");

        // Print summary
        int totalGen = zoneCapacities.values().stream()
            .mapToInt(z -> z.targetGeneration).sum();
        int totalAtt = zoneCapacities.values().stream()
            .mapToInt(z -> z.targetAttraction).sum();

        System.out.println("[G-A Balance] Total target generation: " + totalGen + " trips/day");
        System.out.println("[G-A Balance] Total target attraction: " + totalAtt + " trips/day");
    }

    /**
     * Check if a zone can generate more trips (has remaining capacity).
     *
     * @param zoneId Zone identifier
     * @return true if zone has generation capacity remaining
     */
    public boolean canGenerate(String zoneId) {
        if (!zoneCapacities.containsKey(zoneId)) {
            return true; // no constraint for unmapped zones
        }

        ZoneCapacity capacity = zoneCapacities.get(zoneId);
        int current = currentGeneration.getOrDefault(zoneId, 0);

        // Allow up to 105% of target (tight tolerance for reduced empty trip model)
        return current < (capacity.targetGeneration * 1.05);
    }

    /**
     * Check if a zone can attract more trips (has remaining capacity).
     *
     * @param zoneId Zone identifier
     * @return true if zone has attraction capacity remaining
     */
    public boolean canAttract(String zoneId) {
        if (!zoneCapacities.containsKey(zoneId)) {
            return true; // no constraint for unmapped zones
        }

        ZoneCapacity capacity = zoneCapacities.get(zoneId);
        int current = currentAttraction.getOrDefault(zoneId, 0);

        // Allow up to 105% of target (tight tolerance for reduced empty trip model)
        return current < (capacity.targetAttraction * 1.05);
    }

    /**
     * Get remaining attraction capacity for a zone (for weighted selection).
     *
     * @param zoneId Zone identifier
     * @return Remaining capacity as fraction [0.0, 1.0]
     */
    public double getRemainingAttractionCapacity(String zoneId) {
        if (!zoneCapacities.containsKey(zoneId)) {
            return 1.0; // full capacity for unmapped zones
        }

        ZoneCapacity capacity = zoneCapacities.get(zoneId);
        int current = currentAttraction.getOrDefault(zoneId, 0);
        int remaining = capacity.targetAttraction - current;

        if (remaining <= 0) return 0.0;

        return (double) remaining / capacity.targetAttraction;
    }

    /**
     * Select a balanced destination zone from candidates.
     * Weights selection by:
     * 1. O-D probability from matrix
     * 2. Remaining attraction capacity
     *
     * @param originZoneId Origin zone
     * @param candidates Map of candidate destination zones with O-D probabilities
     * @return Selected destination zone ID, or null if no valid destination
     */
    public String selectBalancedDestination(String originZoneId,
                                           Map<String, Double> candidates) {
        // Filter candidates by attraction capacity
        Map<String, Double> validCandidates = new HashMap<>();

        for (Map.Entry<String, Double> entry : candidates.entrySet()) {
            String destZone = entry.getKey();
            double odProb = entry.getValue();

            if (canAttract(destZone)) {
                double remainingCapacity = getRemainingAttractionCapacity(destZone);

                // Combined weight: O-D probability * (0.5 + 0.5 * remaining capacity)
                // This ensures destinations with more capacity are favored
                double weight = odProb * (0.5 + 0.5 * remainingCapacity);
                validCandidates.put(destZone, weight);
            }
        }

        if (validCandidates.isEmpty()) {
            return null; // no valid destinations with capacity
        }

        // Weighted random selection
        double totalWeight = validCandidates.values().stream()
            .mapToDouble(Double::doubleValue).sum();

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : validCandidates.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback: return first valid candidate
        return validCandidates.keySet().iterator().next();
    }

    /**
     * Record a generated trip (update generation and attraction counts).
     *
     * @param originZoneId Origin zone
     * @param destZoneId Destination zone
     */
    public void recordTrip(String originZoneId, String destZoneId) {
        // Update generation count
        currentGeneration.merge(originZoneId, 1, Integer::sum);

        // Update attraction count
        currentAttraction.merge(destZoneId, 1, Integer::sum);
    }

    /**
     * Helper method to repeat a string (Java 8 compatible).
     */
    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Print generation-attraction balance summary.
     */
    public void printSummary() {
        System.out.println("\n" + repeatString("=", 90));
        System.out.println("GENERATION-ATTRACTION BALANCE SUMMARY");
        System.out.println(repeatString("=", 90));

        // Calculate totals
        int totalGenTarget = 0;
        int totalGenActual = 0;
        int totalAttTarget = 0;
        int totalAttActual = 0;

        System.out.println("\nZone-by-Zone Balance:");
        System.out.println(String.format("%-8s %-12s %-12s %-12s %-12s %-10s %-10s",
            "Zone", "Gen Target", "Gen Actual", "Att Target", "Att Actual", "Gen Error%", "Att Error%"));
        System.out.println(repeatString("-", 90));

        // Sort zones by ID for consistent output
        List<String> sortedZones = new ArrayList<>(zoneCapacities.keySet());
        Collections.sort(sortedZones);

        for (String zoneId : sortedZones) {
            ZoneCapacity capacity = zoneCapacities.get(zoneId);
            int genTarget = capacity.targetGeneration;
            int attTarget = capacity.targetAttraction;
            int genActual = currentGeneration.getOrDefault(zoneId, 0);
            int attActual = currentAttraction.getOrDefault(zoneId, 0);

            double genError = genTarget > 0 ?
                100.0 * (genActual - genTarget) / genTarget : 0.0;
            double attError = attTarget > 0 ?
                100.0 * (attActual - attTarget) / attTarget : 0.0;

            System.out.println(String.format("%-8s %-12d %-12d %-12d %-12d %9.2f%% %9.2f%%",
                zoneId, genTarget, genActual, attTarget, attActual, genError, attError));

            totalGenTarget += genTarget;
            totalGenActual += genActual;
            totalAttTarget += attTarget;
            totalAttActual += attActual;
        }

        System.out.println(repeatString("-", 90));

        // System-wide totals
        double systemGenError = totalGenTarget > 0 ?
            100.0 * (totalGenActual - totalGenTarget) / totalGenTarget : 0.0;
        double systemAttError = totalAttTarget > 0 ?
            100.0 * (totalAttActual - totalAttTarget) / totalAttTarget : 0.0;

        System.out.println(String.format("%-8s %-12d %-12d %-12d %-12d %9.2f%% %9.2f%%",
            "TOTAL", totalGenTarget, totalGenActual, totalAttTarget, totalAttActual,
            systemGenError, systemAttError));

        // Mass balance check
        System.out.println("\nMass Balance Check:");
        System.out.println("  Total generation: " + totalGenActual + " trips");
        System.out.println("  Total attraction: " + totalAttActual + " trips");

        double balanceError = Math.max(totalGenActual, totalAttActual) > 0 ?
            100.0 * Math.abs(totalGenActual - totalAttActual) /
            Math.max(totalGenActual, totalAttActual) : 0.0;

        System.out.println("  Balance error: " +
            String.format("%.2f%%", balanceError));

        // Grade assessment
        double avgAbsError = (Math.abs(systemGenError) + Math.abs(systemAttError)) / 2.0;
        String grade;
        if (avgAbsError < 5.0) grade = "A (Excellent)";
        else if (avgAbsError < 10.0) grade = "B (Good)";
        else if (avgAbsError < 15.0) grade = "C (Acceptable)";
        else grade = "D (Needs Improvement)";

        System.out.println("\nG-A Balance Grade: " + grade +
            " (avg error: " + String.format("%.2f%%", avgAbsError) + ")");

        System.out.println(repeatString("=", 90));
    }

    /**
     * Get balance statistics for validation.
     *
     * @return Map with keys: "gen_error_pct", "att_error_pct", "balance_error_pct"
     */
    public Map<String, Double> getBalanceStats() {
        int totalGenTarget = 0;
        int totalGenActual = 0;
        int totalAttTarget = 0;
        int totalAttActual = 0;

        for (ZoneCapacity capacity : zoneCapacities.values()) {
            String zoneId = capacity.zoneId;
            totalGenTarget += capacity.targetGeneration;
            totalGenActual += currentGeneration.getOrDefault(zoneId, 0);
            totalAttTarget += capacity.targetAttraction;
            totalAttActual += currentAttraction.getOrDefault(zoneId, 0);
        }

        double genError = totalGenTarget > 0 ?
            100.0 * Math.abs(totalGenActual - totalGenTarget) / totalGenTarget : 0.0;
        double attError = totalAttTarget > 0 ?
            100.0 * Math.abs(totalAttActual - totalAttTarget) / totalAttTarget : 0.0;
        double balanceError = Math.max(totalGenActual, totalAttActual) > 0 ?
            100.0 * Math.abs(totalGenActual - totalAttActual) /
            Math.max(totalGenActual, totalAttActual) : 0.0;

        Map<String, Double> stats = new HashMap<>();
        stats.put("gen_error_pct", genError);
        stats.put("att_error_pct", attError);
        stats.put("balance_error_pct", balanceError);

        return stats;
    }
}
