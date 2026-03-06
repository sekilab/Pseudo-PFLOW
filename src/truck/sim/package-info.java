/**
 * Tokyo Metropolitan Area Truck Freight Simulation Framework
 *
 * <p>This package provides a comprehensive agent-based model (ABM) for simulating
 * truck freight movements across the Tokyo Metropolitan Area. The simulation models
 * 1.43 million trucks operating across 73 MFS zones with realistic trip generation,
 * destination selection, cargo logistics, and validation.
 *
 * <h2>Core Components</h2>
 *
 * <h3>Main Orchestrator</h3>
 * <ul>
 *   <li>{@link truck.sim.TruckSimulation} - Main simulation engine and entry point</li>
 * </ul>
 *
 * <h3>Agent & Trip Modeling</h3>
 * <ul>
 *   <li>{@link truck.sim.TruckAgent} - Individual truck agent with behavioral modeling</li>
 *   <li>{@link truck.sim.TruckTrip} - Trip data structure (origin, destination, cargo, time)</li>
 *   <li>{@link truck.sim.TruckType} - Truck operational types (DELIVERY, LONG_HAUL, MIXED)</li>
 *   <li>{@link truck.sim.TruckStatus} - Cargo status (LOADED, EMPTY)</li>
 * </ul>
 *
 * <h3>POI & Destination Management</h3>
 * <ul>
 *   <li>{@link truck.sim.POIManager} - Point of Interest management with water validation</li>
 *   <li>{@link truck.sim.PointOfInterest} - POI data structure (586+ delivery destinations)</li>
 *   <li>{@link truck.sim.FacilityType} - Facility classification (logistics, retail, industrial)</li>
 * </ul>
 *
 * <h3>Geography & Zones</h3>
 * <ul>
 *   <li>{@link truck.sim.DeliveryZone} - MFS zone representation (73 zones)</li>
 *   <li>{@link truck.sim.MetropolitanConfig} - Metropolitan area configuration</li>
 * </ul>
 *
 * <h3>Trip Generation & Routing</h3>
 * <ul>
 *   <li>{@link truck.sim.TripGenerator} - Probabilistic trip generation</li>
 *   <li>{@link truck.sim.CommodityRouter} - Commodity-based routing logic (9 types)</li>
 *   <li>{@link truck.sim.OriginDestinationMatrix} - O-D probability matrix (70×70)</li>
 *   <li>{@link truck.sim.GenerationAttractionBalancer} - G-A balance for realistic distribution</li>
 * </ul>
 *
 * <h3>Configuration & I/O</h3>
 * <ul>
 *   <li>{@link truck.sim.TruckConfig} - Configuration management</li>
 *   <li>{@link truck.sim.TruckDataExporter} - CSV export with sim_day support</li>
 * </ul>
 *
 * <h3>Validation & Metrics</h3>
 * <ul>
 *   <li>{@link truck.sim.ValidationEngine} - 51-metric validation suite</li>
 *   <li>{@link truck.sim.MetricsTracker} - Real-time metrics collection</li>
 *   <li>{@link truck.sim.SimulationMetrics} - Metrics aggregation</li>
 * </ul>
 *
 * <h2>Key Features (v2.1)</h2>
 *
 * <h3>Water Body Validation</h3>
 * <p>Comprehensive water body validation prevents trips to invalid locations
 * (Tokyo Bay, Pacific Ocean, rivers). Three-layer validation approach:
 * <ol>
 *   <li>Load-time filtering: Skip invalid POIs when loading CSV files</li>
 *   <li>Selection filtering: Filter water POIs during destination selection</li>
 *   <li>Pre-use checks: Final validation before coordinate use</li>
 * </ol>
 * <p>Result: Zero trips to water bodies (validated with 586 POIs)
 *
 * <h3>POI Expansion</h3>
 * <p>586 valid Points of Interest across 73 MFS zones:
 * <ul>
 *   <li>Logistics centers: 162 (+86% from baseline)</li>
 *   <li>Retail shops: 212 (+48%)</li>
 *   <li>Industrial sites: 120 (+67%)</li>
 *   <li>Shopping malls: 57</li>
 *   <li>Port terminals: 35</li>
 * </ul>
 *
 * <h3>Temporal Support</h3>
 * <p>Multi-day trip tracking with sim_day column:
 * <ul>
 *   <li>sim_day: Integer day number (0, 1, 2, ...)</li>
 *   <li>starttime: Normalized to 0-86,399 seconds per day</li>
 *   <li>Exported in all CSV outputs</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // Compile
 * javac -source 8 -target 8 -d bin -sourcepath src src/truck/sim/TruckSimulation.java
 *
 * // Run full scale (1.43M trucks)
 * java -Xmx16G -Xms8G -XX:+UseG1GC -cp bin truck.sim.TruckSimulation
 * </pre>
 *
 * <h2>Output</h2>
 * <p>Simulation generates CSV files in {@code data/output/truck/run_YYYYMMDD_HHMMSS/}:
 * <ul>
 *   <li>trips.csv - All trips (delivery + empty)</li>
 *   <li>trips_pseudo_pflow.csv - PFLOW format with sim_day column</li>
 *   <li>trips_with_zones.csv - Trips with zone assignments</li>
 *   <li>trucks.csv - Fleet roster with home locations</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>51-metric validation suite compares against MLIT targets:
 * <ul>
 *   <li>Grade A+: 51/51 tests passed (current status)</li>
 *   <li>Fleet mix validation</li>
 *   <li>Commodity distribution</li>
 *   <li>Generation-attraction balance</li>
 *   <li>Distance and volume metrics</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>Full scale (1.43M trucks, 586 POIs):
 * <ul>
 *   <li>Runtime: ~3 minutes</li>
 *   <li>Memory: ~12 GB peak</li>
 *   <li>Throughput: ~478,000 trucks/minute</li>
 *   <li>Output: 1.2 GB total</li>
 * </ul>
 *
 * <h2>Version</h2>
 * <p>Current: v2.1 (2026-02-15) - Water Validation & POI Expansion
 *
 * @author Tokyo Truck ABM Framework Team
 * @version 2.1
 * @since 1.0
 */
package truck.sim;
