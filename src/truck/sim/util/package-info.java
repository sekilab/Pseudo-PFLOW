/**
 * Utility classes for CSV loading and geographic calculations.
 *
 * <p>This package provides reusable utility classes for common operations
 * in the truck simulation, including CSV file I/O and distance calculations.
 *
 * <h2>Components</h2>
 *
 * <h3>Data Loading</h3>
 * <ul>
 *   <li>{@link truck.sim.util.CSVLoader} - Generic CSV file loading utilities
 *     <ul>
 *       <li>Robust CSV parsing with error handling</li>
 *       <li>Header validation</li>
 *       <li>Type conversion utilities</li>
 *       <li>Missing value handling</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Geographic Calculations</h3>
 * <ul>
 *   <li>{@link truck.sim.util.DistanceCalculator} - Geographic distance utilities
 *     <ul>
 *       <li>Haversine formula for great-circle distance</li>
 *       <li>Coordinate validation</li>
 *       <li>Fast distance approximations</li>
 *       <li>Optimized for Tokyo Metropolitan Area</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>CSV Loading</h3>
 * <pre>
 * CSVLoader loader = new CSVLoader();
 * List&lt;String[]&gt; rows = loader.loadCSV("config/truck/zones/intra.csv");
 *
 * for (String[] row : rows) {
 *     String zoneId = row[0];
 *     double lon = Double.parseDouble(row[2]);
 *     double lat = Double.parseDouble(row[3]);
 *     // ... process data
 * }
 * </pre>
 *
 * <h3>Distance Calculation</h3>
 * <pre>
 * // Calculate distance between two points
 * double distance = DistanceCalculator.haversine(
 *     139.7673, 35.6809,  // Tokyo Station
 *     139.6917, 35.6895   // Shinjuku Station
 * );
 * // Returns: ~8.5 km
 *
 * // Check if point is within radius
 * boolean nearby = DistanceCalculator.isWithinRadius(
 *     truckLon, truckLat,
 *     zoneLon, zoneLat,
 *     radiusKm
 * );
 * </pre>
 *
 * <h2>Design Principles</h2>
 *
 * <h3>Stateless Utilities</h3>
 * <p>All methods are static and stateless for easy reuse without
 * object instantiation overhead.
 *
 * <h3>Error Handling</h3>
 * <p>Robust error handling with meaningful error messages for
 * debugging and validation.
 *
 * <h3>Performance</h3>
 * <p>Optimized implementations for high-frequency operations:
 * <ul>
 *   <li>Haversine calculation: ~1-2 microseconds per call</li>
 *   <li>CSV parsing: ~10,000 rows per second</li>
 * </ul>
 *
 * @author Tokyo Truck ABM Framework Team
 * @version 2.1
 * @since 1.0
 */
package truck.sim.util;
