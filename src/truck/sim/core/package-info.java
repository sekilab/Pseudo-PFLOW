/**
 * Core simulation engines for truck fleet initialization and trip generation.
 *
 * <p>This package contains the fundamental engine components that drive the
 * truck simulation. These classes are responsible for fleet creation, trip
 * generation algorithms, and zone initialization.
 *
 * <h2>Components</h2>
 *
 * <h3>Fleet Management</h3>
 * <ul>
 *   <li>{@link truck.sim.core.FleetFactory} - Truck fleet initialization
 *     <ul>
 *       <li>Creates 1.43M truck agents from configuration</li>
 *       <li>Assigns home locations within zones</li>
 *       <li>Distributes fleet mix (heavy/medium/small/light)</li>
 *       <li>Sets truck types (DELIVERY/LONG_HAUL/MIXED_OPERATION)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Trip Generation</h3>
 * <ul>
 *   <li>{@link truck.sim.core.TripGenerationEngine} - Core trip generation logic
 *     <ul>
 *       <li>Stochastic trip timing based on time periods</li>
 *       <li>Load/empty trip pairing for realistic operations</li>
 *       <li>Commodity assignment from router</li>
 *       <li>Destination selection with water validation</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Zone Management</h3>
 * <ul>
 *   <li>{@link truck.sim.core.ZoneInitializer} - Zone loading and configuration
 *     <ul>
 *       <li>Loads 37 INTRA zones from zones/intra.csv</li>
 *       <li>Loads 33 INTER zones from zones/inter.csv</li>
 *       <li>Merges zones for dual-mode simulation (70 total)</li>
 *       <li>Validates zone properties and coordinates</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Design Patterns</h2>
 *
 * <h3>Factory Pattern</h3>
 * <p>{@code FleetFactory} uses the factory pattern to create truck agents
 * with consistent properties and initialization.
 *
 * <h3>Engine Pattern</h3>
 * <p>{@code TripGenerationEngine} encapsulates complex trip generation
 * algorithms into a reusable engine component.
 *
 * <h3>Initializer Pattern</h3>
 * <p>{@code ZoneInitializer} separates zone loading logic from the main
 * simulation orchestrator for better modularity.
 *
 * <h2>Data Flow</h2>
 *
 * <pre>
 * 1. ZoneInitializer
 *    ↓ (loads zones/intra.csv + zones/inter.csv)
 *    DeliveryZone[] (70 zones)
 *
 * 2. FleetFactory
 *    ↓ (creates trucks with home locations)
 *    TruckAgent[] (1.43M trucks)
 *
 * 3. TripGenerationEngine
 *    ↓ (generates trips with O-D matrix + POI selection)
 *    TruckTrip[] (2.5M+ trips)
 * </pre>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // 1. Initialize zones
 * ZoneInitializer zoneInit = new ZoneInitializer();
 * List&lt;DeliveryZone&gt; zones = zoneInit.loadZones("config/truck/zones/");
 *
 * // 2. Create fleet
 * FleetFactory factory = new FleetFactory(random, config);
 * List&lt;TruckAgent&gt; fleet = factory.createFleet(zones, 1434510);
 *
 * // 3. Generate trips
 * TripGenerationEngine engine = new TripGenerationEngine(random, config);
 * List&lt;TruckTrip&gt; trips = engine.generateTrips(fleet, zones, poiManager, odMatrix);
 * </pre>
 *
 * @author Tokyo Truck ABM Framework Team
 * @version 2.1
 * @since 2.0
 */
package truck.sim.core;
