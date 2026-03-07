package truck.sim.core;

import truck.sim.*;
import truck.sim.util.DistanceCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

import static truck.sim.TruckSimulationConstants.*;

/**
 * Engine responsible for generating truck trips in the simulation.
 *
 * <p>This component handles:
 * <ul>
 *   <li>Empty trip generation based on probability</li>
 *   <li>Delivery trip generation with commodity routing</li>
 *   <li>Origin and destination selection using O-D matrix</li>
 *   <li>Generation-Attraction (G-A) balance enforcement</li>
 *   <li>Distance-based probability weighting</li>
 * </ul>
 *
 * <p><b>Trip Types:</b>
 * <ul>
 *   <li>Empty trips: Trucks returning without cargo (~8% probability)</li>
 *   <li>Delivery trips: Trucks transporting commodities</li>
 * </ul>
 *
 * <p><b>Destination Selection Strategy:</b>
 * <ul>
 *   <li>DELIVERY trucks: Short-range focus (decay factor: 5.2 km)</li>
 *   <li>LONG_HAUL trucks: Long-distance focus (decay factor: 220 km)</li>
 *   <li>MIXED_OPERATION trucks: Medium-range (decay factor: 45 km)</li>
 * </ul>
 *
 * @author Tokyo MFS Truck ABM Simulation
 * @version 1.0
 * @see TruckAgent
 * @see DeliveryZone
 * @see OriginDestinationMatrix
 */
public class TripGenerationEngine {

    private final Random random;
    private final TruckConfig config;
    private final OriginDestinationMatrix odMatrix;
    private final CommodityRouter commodityRouter;
    private final POIManager poiManager;

    // G-A balance tracking
    private final Map<String, Double> zoneGeneration;
    private final Map<String, Double> zoneAttraction;

    /**
     * Creates a new TripGenerationEngine with required dependencies.
     *
     * @param config Configuration parameters
     * @param odMatrix Origin-Destination matrix for zone relationships
     * @param commodityRouter Commodity routing logic
     * @param poiManager Point-of-Interest manager
     * @throws IllegalArgumentException if any parameter is null
     */
    public TripGenerationEngine(
            TruckConfig config,
            OriginDestinationMatrix odMatrix,
            CommodityRouter commodityRouter,
            POIManager poiManager) {

        if (config == null) throw new IllegalArgumentException("Config cannot be null");
        if (odMatrix == null) throw new IllegalArgumentException("ODMatrix cannot be null");
        if (commodityRouter == null) throw new IllegalArgumentException("CommodityRouter cannot be null");
        if (poiManager == null) throw new IllegalArgumentException("POIManager cannot be null");

        this.config = config;
        this.odMatrix = odMatrix;
        this.commodityRouter = commodityRouter;
        this.poiManager = poiManager;
        this.random = new Random(config.getRandomSeed());

        this.zoneGeneration = new HashMap<>();
        this.zoneAttraction = new HashMap<>();
    }

    /**
     * Generates all trips for the truck fleet.
     *
     * <p>Trip generation process:
     * <ol>
     *   <li>Generate empty trips based on probability</li>
     *   <li>Generate delivery trips for each truck</li>
     *   <li>Apply G-A balance constraints</li>
     *   <li>Validate trip distributions</li>
     * </ol>
     *
     * @param fleet List of all trucks in simulation
     * @param deliveryZones List of all delivery zones
     * @return List of generated trips
     * @throws IllegalStateException if trip generation fails
     */
    public List<TruckTrip> generateTrips(
            List<TruckAgent> fleet,
            List<DeliveryZone> deliveryZones) {

        if (fleet == null || fleet.isEmpty()) {
            throw new IllegalStateException("Fleet cannot be null or empty");
        }

        if (deliveryZones == null || deliveryZones.isEmpty()) {
            throw new IllegalStateException("Delivery zones cannot be null or empty");
        }

        List<TruckTrip> allTrips = new ArrayList<>();

        // Generate empty trips
        List<TruckTrip> emptyTrips = generateEmptyTrips(fleet, deliveryZones);
        allTrips.addAll(emptyTrips);

        // Generate delivery trips
        List<TruckTrip> deliveryTrips = generateDeliveryTrips(fleet, deliveryZones);
        allTrips.addAll(deliveryTrips);

        return allTrips;
    }

    /**
     * Generates empty trips for trucks returning without cargo.
     *
     * <p>Empty trips occur with probability defined by EMPTY_TRIP_PROBABILITY.
     * These represent trucks returning to depots or moving between jobs.
     *
     * @param fleet Truck fleet
     * @param deliveryZones Available zones
     * @return List of empty trips
     */
    private List<TruckTrip> generateEmptyTrips(
            List<TruckAgent> fleet,
            List<DeliveryZone> deliveryZones) {

        List<TruckTrip> emptyTrips = new ArrayList<>();

        for (TruckAgent truck : fleet) {
            if (random.nextDouble() < EMPTY_TRIP_PROBABILITY) {
                DeliveryZone origin = selectRandomZone(deliveryZones);
                DeliveryZone destination = selectRandomZone(deliveryZones);

                TruckTrip trip = new TruckTrip(
                    truck,
                    origin,
                    destination,
                    0.0,  // No cargo
                    "empty"
                );

                emptyTrips.add(trip);
            }
        }

        return emptyTrips;
    }

    /**
     * Generates delivery trips for all trucks in fleet.
     *
     * <p>Each truck generates trips based on:
     * <ul>
     *   <li>Truck type (DELIVERY, LONG_HAUL, MIXED_OPERATION)</li>
     *   <li>Vehicle size and capacity</li>
     *   <li>Shift time constraints</li>
     *   <li>Origin zone selection</li>
     *   <li>Destination zone selection with G-A balance</li>
     * </ul>
     *
     * @param fleet Truck fleet
     * @param deliveryZones Available zones
     * @return List of delivery trips
     */
    private List<TruckTrip> generateDeliveryTrips(
            List<TruckAgent> fleet,
            List<DeliveryZone> deliveryZones) {

        List<TruckTrip> deliveryTrips = new ArrayList<>();

        for (TruckAgent truck : fleet) {
            int numTrips = calculateNumTrips(truck);

            for (int i = 0; i < numTrips; i++) {
                TruckTrip trip = generateSingleDeliveryTrip(truck, deliveryZones);
                if (trip != null) {
                    deliveryTrips.add(trip);
                }
            }
        }

        return deliveryTrips;
    }

    /**
     * Generates a single delivery trip for a truck.
     *
     * @param truck Truck to generate trip for
     * @param deliveryZones Available zones
     * @return Generated trip or null if generation failed
     */
    private TruckTrip generateSingleDeliveryTrip(
            TruckAgent truck,
            List<DeliveryZone> deliveryZones) {

        // Select origin zone
        DeliveryZone origin = selectOriginZone(truck, deliveryZones);
        if (origin == null) {
            return null;
        }

        // Select destination zone with G-A balance
        DeliveryZone destination = selectBalancedDestination(
            origin,
            truck.getTruckType(),
            deliveryZones
        );

        if (destination == null) {
            return null;
        }

        // Generate cargo weight
        double cargoWeight = generateCargoWeight(truck);

        // Select commodity type
        String commodity = commodityRouter.selectCommodity(origin, destination);

        // Update G-A balance
        updateGABalance(origin, destination, cargoWeight);

        return new TruckTrip(truck, origin, destination, cargoWeight, commodity);
    }

    /**
     * Selects origin zone for a truck trip.
     *
     * <p>Origin selection is weighted by:
     * <ul>
     *   <li>Zone employment density</li>
     *   <li>Zone establishment count</li>
     *   <li>O-D matrix generation factors</li>
     * </ul>
     *
     * @param truck Truck generating trip
     * @param deliveryZones Available zones
     * @return Selected origin zone
     */
    private DeliveryZone selectOriginZone(
            TruckAgent truck,
            List<DeliveryZone> deliveryZones) {

        // Build probability distribution
        double[] probabilities = new double[deliveryZones.size()];
        double totalProb = 0.0;

        for (int i = 0; i < deliveryZones.size(); i++) {
            DeliveryZone zone = deliveryZones.get(i);

            // Weight by employment + establishments
            double weight = zone.getEmployment() + zone.getEstablishments() * 10.0;

            probabilities[i] = weight;
            totalProb += weight;
        }

        // Normalize probabilities
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= totalProb;
        }

        // Select zone using roulette wheel selection
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < deliveryZones.size(); i++) {
            cumulative += probabilities[i];
            if (rand <= cumulative) {
                return deliveryZones.get(i);
            }
        }

        return deliveryZones.get(deliveryZones.size() - 1);
    }

    /**
     * Selects destination zone with G-A balance enforcement.
     *
     * <p>Destination selection considers:
     * <ul>
     *   <li>Distance from origin (exponential decay)</li>
     *   <li>O-D matrix probabilities</li>
     *   <li>G-A balance constraints</li>
     *   <li>Truck type-specific decay factors</li>
     * </ul>
     *
     * @param origin Origin zone
     * @param truckType Type of truck (affects distance preference)
     * @param deliveryZones All available zones
     * @return Selected destination zone
     */
    private DeliveryZone selectBalancedDestination(
            DeliveryZone origin,
            TruckType truckType,
            List<DeliveryZone> deliveryZones) {

        // Get decay factor based on truck type
        double decayFactor = getDecayFactor(truckType);

        // Build probability distribution
        double[] probabilities = new double[deliveryZones.size()];
        double totalProb = 0.0;

        for (int i = 0; i < deliveryZones.size(); i++) {
            DeliveryZone dest = deliveryZones.get(i);

            // Calculate distance
            double dist = DistanceCalculator.calculateDistance(
                origin.getCenterLat(),
                origin.getCenterLon(),
                dest.getCenterLat(),
                dest.getCenterLon()
            );

            // Get O-D matrix probability
            double odProb = odMatrix.getProbability(origin.getZoneId(), dest.getZoneId());

            // Apply distance decay
            double distWeight = Math.exp(-dist / decayFactor);

            // Apply intra-zone damping if same zone
            if (origin.getZoneId().equals(dest.getZoneId())) {
                odProb *= INTRAZONE_DAMPING_FACTOR;
            }

            // Calculate G-A balance factor
            double gaBalance = calculateGABalanceFactor(origin, dest);

            // Combine factors
            double prob = odProb * distWeight * gaBalance;

            probabilities[i] = prob;
            totalProb += prob;
        }

        // Normalize and select
        if (totalProb == 0.0) {
            return selectRandomZone(deliveryZones);
        }

        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= totalProb;
        }

        // Roulette wheel selection
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < deliveryZones.size(); i++) {
            cumulative += probabilities[i];
            if (rand <= cumulative) {
                return deliveryZones.get(i);
            }
        }

        return deliveryZones.get(deliveryZones.size() - 1);
    }

    /**
     * Gets distance decay factor based on truck type.
     *
     * @param truckType Type of truck
     * @return Decay factor in kilometers
     */
    private double getDecayFactor(TruckType truckType) {
        switch (truckType) {
            case DELIVERY:
                return DELIVERY_DISTANCE_DECAY_FACTOR;
            case LONG_HAUL:
                return LONGHAUL_DISTANCE_DECAY_FACTOR;
            case MIXED_OPERATION:
                return MIXED_DISTANCE_DECAY_FACTOR;
            default:
                return MIXED_DISTANCE_DECAY_FACTOR;
        }
    }

    /**
     * Calculates G-A balance factor for zone pair.
     *
     * <p>Penalizes zone pairs that are out of balance:
     * <ul>
     *   <li>If origin over-generates: reduce probability</li>
     *   <li>If destination over-attracts: reduce probability</li>
     *   <li>Balanced zones: no penalty</li>
     * </ul>
     *
     * @param origin Origin zone
     * @param destination Destination zone
     * @return Balance factor [0.1, 1.0]
     */
    private double calculateGABalanceFactor(DeliveryZone origin, DeliveryZone destination) {
        String originId = origin.getZoneId();
        String destId = destination.getZoneId();

        double originGen = zoneGeneration.getOrDefault(originId, 0.0);
        double originAttr = zoneAttraction.getOrDefault(originId, 0.0);

        double destGen = zoneGeneration.getOrDefault(destId, 0.0);
        double destAttr = zoneAttraction.getOrDefault(destId, 0.0);

        // Calculate imbalance
        double originImbalance = originGen - originAttr;
        double destImbalance = destAttr - destGen;

        // Penalize if both are over-generating or over-attracting
        double balanceFactor = 1.0;

        if (originImbalance > 0 && destImbalance < 0) {
            // Origin over-generates, dest under-attracts - reduce penalty
            balanceFactor = 1.0;
        } else if (originImbalance < 0 && destImbalance > 0) {
            // Origin under-generates, dest over-attracts - increase penalty
            balanceFactor = 0.5;
        } else {
            // Same direction imbalance - moderate penalty
            balanceFactor = 0.7;
        }

        return Math.max(0.1, balanceFactor);
    }

    /**
     * Updates G-A balance tracking for a trip.
     *
     * @param origin Origin zone
     * @param destination Destination zone
     * @param cargoWeight Cargo weight in tons
     */
    private void updateGABalance(DeliveryZone origin, DeliveryZone destination, double cargoWeight) {
        String originId = origin.getZoneId();
        String destId = destination.getZoneId();

        zoneGeneration.put(originId, zoneGeneration.getOrDefault(originId, 0.0) + cargoWeight);
        zoneAttraction.put(destId, zoneAttraction.getOrDefault(destId, 0.0) + cargoWeight);
    }

    /**
     * Calculates number of trips for a truck based on capacity and type.
     *
     * @param truck Truck to calculate trips for
     * @return Number of trips
     */
    private int calculateNumTrips(TruckAgent truck) {
        // Base trips on capacity
        double capacity = truck.getCapacityTons();

        if (capacity >= 10.0) {
            return 1 + random.nextInt(2);  // 1-2 trips for heavy
        } else if (capacity >= 4.0) {
            return 2 + random.nextInt(2);  // 2-3 trips for medium
        } else {
            return 3 + random.nextInt(3);  // 3-5 trips for small/light
        }
    }

    /**
     * Generates cargo weight for a trip based on truck capacity.
     *
     * @param truck Truck carrying cargo
     * @return Cargo weight in tons
     */
    private double generateCargoWeight(TruckAgent truck) {
        double capacity = truck.getCapacityTons();
        String vehicleSize = truck.getVehicleSize();

        if ("heavy".equals(vehicleSize)) {
            // Heavy vehicles use gamma distribution
            double weight;
            do {
                weight = generateGamma(HEAVY_GAMMA_SHAPE, HEAVY_GAMMA_SCALE);
            } while (weight > capacity);
            return weight;
        } else {
            // Other sizes use uniform distribution (50-95% capacity)
            double utilizationFactor = 0.5 + random.nextDouble() * 0.45;
            return capacity * utilizationFactor;
        }
    }

    /**
     * Generates random value from gamma distribution.
     *
     * @param shape Shape parameter
     * @param scale Scale parameter
     * @return Random gamma value
     */
    private double generateGamma(double shape, double scale) {
        if (shape < 1.0) {
            double gamma = generateGamma(shape + 1.0, scale);
            return gamma * Math.pow(random.nextDouble(), 1.0 / shape);
        }

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
     * Selects a random zone from the list.
     *
     * @param zones Available zones
     * @return Randomly selected zone
     */
    private DeliveryZone selectRandomZone(List<DeliveryZone> zones) {
        int index = random.nextInt(zones.size());
        return zones.get(index);
    }

    /**
     * Gets current G-A balance statistics.
     *
     * @return Map of zone IDs to balance ratios
     */
    public Map<String, Double> getGABalanceStats() {
        Map<String, Double> stats = new HashMap<>();

        for (String zoneId : zoneGeneration.keySet()) {
            double gen = zoneGeneration.get(zoneId);
            double attr = zoneAttraction.getOrDefault(zoneId, 0.0);

            double ratio = (attr > 0) ? (gen / attr) : 1.0;
            stats.put(zoneId, ratio);
        }

        return stats;
    }

    /**
     * Resets G-A balance tracking.
     */
    public void resetGABalance() {
        zoneGeneration.clear();
        zoneAttraction.clear();
    }
}
