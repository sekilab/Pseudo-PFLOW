/**
 * Reserved package for future truck simulation engine components.
 *
 * <p>Core simulation logic is organized across the following classes:
 * <ul>
 *   <li>{@link truck.sim.TruckSimulation} — Orchestrator (initialization, main loop)</li>
 *   <li>{@link truck.sim.DestinationSelector} — All destination selection strategies</li>
 *   <li>{@link truck.sim.ZoneManager} — Zone lookup and distance calculation</li>
 *   <li>{@link truck.sim.spatial.GeoValidator} — Multi-layer spatial validation</li>
 *   <li>{@link truck.sim.spatial.PointGenerator} — Coordinate generation with land-use filtering</li>
 * </ul>
 *
 * @author Truck ABM Framework
 * @version 2.1
 */
package truck.sim.core;
