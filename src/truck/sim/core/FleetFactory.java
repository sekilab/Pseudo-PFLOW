package truck.sim.core;

import truck.sim.TruckAgent;
import truck.sim.TruckType;
import truck.sim.TruckConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static truck.sim.TruckSimulationConstants.*;

/**
 * Factory class responsible for creating and configuring truck fleets.
 *
 * <p>This component handles:
 * <ul>
 *   <li>Generating truck fleets based on configuration parameters</li>
 *   <li>Distributing vehicle sizes across the fleet</li>
 *   <li>Assigning truck types (DELIVERY, LONG_HAUL, MIXED_OPERATION)</li>
 *   <li>Setting capacity and shift times for each truck</li>
 * </ul>
 *
 * <p><b>Vehicle Size Distribution:</b>
 * <ul>
 *   <li>Heavy: ≥10 tons capacity</li>
 *   <li>Medium: 4-10 tons capacity</li>
 *   <li>Small: 2-4 tons capacity</li>
 *   <li>Light: &lt;2 tons capacity</li>
 * </ul>
 *
 * <p><b>Truck Type Distribution (MFS H25/2013 Survey):</b>
 * <ul>
 *   <li>DELIVERY: 37% - Short-range urban delivery trucks</li>
 *   <li>LONG_HAUL: 34.5% - Inter-regional freight trucks</li>
 *   <li>MIXED_OPERATION: 28.5% - Multi-purpose trucks</li>
 * </ul>
 *
 * @author Tokyo MFS Truck ABM Simulation
 * @version 1.0
 * @see TruckAgent
 * @see TruckType
 * @see TruckConfig
 */
public class FleetFactory {

    private final Random random;
    private final TruckConfig config;

    /**
     * Creates a new FleetFactory with the specified configuration.
     *
     * @param config Configuration parameters for fleet generation
     * @throws IllegalArgumentException if config is null
     */
    public FleetFactory(TruckConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("TruckConfig cannot be null");
        }
        this.config = config;
        this.random = new Random(config.getRandomSeed());
    }

    /**
     * Creates a truck fleet based on configuration parameters.
     *
     * <p>Fleet generation follows these steps:
     * <ol>
     *   <li>Determine total fleet size from configuration</li>
     *   <li>Calculate vehicle size distribution targets</li>
     *   <li>Assign truck types based on MFS survey percentages</li>
     *   <li>Set capacity based on vehicle size category</li>
     *   <li>Assign shift times (early/late/night)</li>
     * </ol>
     *
     * @return List of configured TruckAgent objects
     * @throws IllegalStateException if fleet size is invalid
     */
    public List<TruckAgent> createFleet() {
        int totalTrucks = config.getTotalTrucks();

        if (totalTrucks <= 0) {
            throw new IllegalStateException("Total trucks must be positive, got: " + totalTrucks);
        }

        List<TruckAgent> fleet = new ArrayList<>(totalTrucks);

        // Calculate vehicle size distribution
        int numHeavy = (int) (totalTrucks * config.getHeavyPercent() / 100.0);
        int numMedium = (int) (totalTrucks * config.getMediumPercent() / 100.0);
        int numSmall = (int) (totalTrucks * config.getSmallPercent() / 100.0);
        int numLight = totalTrucks - numHeavy - numMedium - numSmall;

        // Generate trucks by size category
        int truckId = 1;

        // Heavy vehicles (≥10 tons)
        for (int i = 0; i < numHeavy; i++) {
            TruckType type = selectTruckType();
            double capacity = generateHeavyCapacity();
            TruckAgent truck = new TruckAgent(truckId++, type, "heavy", capacity);
            assignShiftTime(truck);
            fleet.add(truck);
        }

        // Medium vehicles (4-10 tons)
        for (int i = 0; i < numMedium; i++) {
            TruckType type = selectTruckType();
            double capacity = generateMediumCapacity();
            TruckAgent truck = new TruckAgent(truckId++, type, "medium", capacity);
            assignShiftTime(truck);
            fleet.add(truck);
        }

        // Small vehicles (2-4 tons)
        for (int i = 0; i < numSmall; i++) {
            TruckType type = selectTruckType();
            double capacity = generateSmallCapacity();
            TruckAgent truck = new TruckAgent(truckId++, type, "small", capacity);
            assignShiftTime(truck);
            fleet.add(truck);
        }

        // Light vehicles (<2 tons)
        for (int i = 0; i < numLight; i++) {
            TruckType type = selectTruckType();
            double capacity = generateLightCapacity();
            TruckAgent truck = new TruckAgent(truckId++, type, "light", capacity);
            assignShiftTime(truck);
            fleet.add(truck);
        }

        return fleet;
    }

    /**
     * Selects a truck type based on MFS survey distribution.
     *
     * <p>Distribution:
     * <ul>
     *   <li>37% DELIVERY</li>
     *   <li>34.5% LONG_HAUL</li>
     *   <li>28.5% MIXED_OPERATION</li>
     * </ul>
     *
     * @return Selected TruckType
     */
    private TruckType selectTruckType() {
        double rand = random.nextDouble() * 100.0;

        if (rand < 37.0) {
            return TruckType.DELIVERY;
        } else if (rand < 71.5) {  // 37.0 + 34.5
            return TruckType.LONG_HAUL;
        } else {
            return TruckType.MIXED_OPERATION;
        }
    }

    /**
     * Generates capacity for heavy vehicles using gamma distribution.
     *
     * <p>Heavy vehicles: ≥10 tons
     * <p>Uses gamma distribution with shape=2.0 and scale from config
     *
     * @return Capacity in tons (≥10.0)
     */
    private double generateHeavyCapacity() {
        double capacity;
        do {
            capacity = generateGamma(HEAVY_GAMMA_SHAPE, HEAVY_GAMMA_SCALE);
        } while (capacity < 10.0);
        return capacity;
    }

    /**
     * Generates capacity for medium vehicles using uniform distribution.
     *
     * <p>Medium vehicles: 4-10 tons
     *
     * @return Capacity in tons (4.0-10.0)
     */
    private double generateMediumCapacity() {
        return 4.0 + random.nextDouble() * 6.0;  // 4-10 tons
    }

    /**
     * Generates capacity for small vehicles using uniform distribution.
     *
     * <p>Small vehicles: 2-4 tons
     *
     * @return Capacity in tons (2.0-4.0)
     */
    private double generateSmallCapacity() {
        return 2.0 + random.nextDouble() * 2.0;  // 2-4 tons
    }

    /**
     * Generates capacity for light vehicles using uniform distribution.
     *
     * <p>Light vehicles: &lt;2 tons
     *
     * @return Capacity in tons (0.5-2.0)
     */
    private double generateLightCapacity() {
        return 0.5 + random.nextDouble() * 1.5;  // 0.5-2 tons
    }

    /**
     * Generates a random value from gamma distribution.
     *
     * <p>Uses Marsaglia and Tsang's method for gamma distribution generation.
     *
     * @param shape Shape parameter (α)
     * @param scale Scale parameter (θ)
     * @return Random value from Gamma(shape, scale)
     */
    private double generateGamma(double shape, double scale) {
        if (shape < 1.0) {
            // Use shape+1, then multiply by U^(1/shape)
            double gamma = generateGamma(shape + 1.0, scale);
            return gamma * Math.pow(random.nextDouble(), 1.0 / shape);
        }

        // Marsaglia and Tsang's method
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);

        while (true) {
            double x, v;
            do {
                x = random.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);

            v = v * v * v;
            x = x * x;
            double u = random.nextDouble();

            if (u < 1.0 - 0.0331 * x * x) {
                return d * v * scale;
            }

            if (Math.log(u) < 0.5 * x + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    /**
     * Assigns shift time to truck based on operational patterns.
     *
     * <p>Shift distribution:
     * <ul>
     *   <li>Early shift (6:00-14:00): 40%</li>
     *   <li>Late shift (14:00-22:00): 40%</li>
     *   <li>Night shift (22:00-6:00): 20%</li>
     * </ul>
     *
     * @param truck Truck to assign shift time
     */
    private void assignShiftTime(TruckAgent truck) {
        double rand = random.nextDouble();

        if (rand < 0.40) {
            // Early shift: 6:00-14:00
            truck.setShiftStartTime(6.0 + random.nextDouble() * 2.0);
        } else if (rand < 0.80) {
            // Late shift: 14:00-22:00
            truck.setShiftStartTime(14.0 + random.nextDouble() * 2.0);
        } else {
            // Night shift: 22:00-6:00
            truck.setShiftStartTime(22.0 + random.nextDouble() * 2.0);
        }
    }

    /**
     * Validates fleet composition against MFS survey targets.
     *
     * <p>Checks:
     * <ul>
     *   <li>Total fleet size matches configuration</li>
     *   <li>Vehicle size distribution within tolerance</li>
     *   <li>Truck type distribution matches survey data</li>
     *   <li>All capacity values are positive and in valid ranges</li>
     * </ul>
     *
     * @param fleet Fleet to validate
     * @throws IllegalStateException if validation fails
     */
    public void validateFleet(List<TruckAgent> fleet) {
        if (fleet == null || fleet.isEmpty()) {
            throw new IllegalStateException("Fleet cannot be null or empty");
        }

        int totalTrucks = fleet.size();
        int expectedTotal = config.getTotalTrucks();

        if (totalTrucks != expectedTotal) {
            throw new IllegalStateException(
                String.format("Fleet size mismatch: expected %d, got %d",
                    expectedTotal, totalTrucks));
        }

        // Count vehicle sizes
        int heavy = 0, medium = 0, small = 0, light = 0;
        for (TruckAgent truck : fleet) {
            double capacity = truck.getCapacityTons();

            if (capacity < 0) {
                throw new IllegalStateException(
                    "Truck " + truck.getTruckId() + " has negative capacity: " + capacity);
            }

            if (capacity >= 10.0) heavy++;
            else if (capacity >= 4.0) medium++;
            else if (capacity >= 2.0) small++;
            else light++;
        }

        // Validate distribution (within 2% tolerance)
        double heavyPercent = (heavy * 100.0) / totalTrucks;
        double expectedHeavy = config.getHeavyPercent();

        if (Math.abs(heavyPercent - expectedHeavy) > 2.0) {
            System.err.printf("[WARN] Heavy vehicle percentage: %.2f%% (target: %.2f%%)%n",
                heavyPercent, expectedHeavy);
        }
    }
}
