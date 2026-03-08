package truck.sim;

import truck.sim.spatial.PointGenerator;
import truck.sim.spatial.TransportNetworkIndex;
import java.util.List;

/**
 * Bundles all subsystems initialized during zone loading.
 *
 * <p>Returned by {@link ZoneLoader#loadSingle()} and {@link ZoneLoader#loadDual(String, String)}
 * to transfer initialized state back to the simulation orchestrator.
 *
 * @version 2.1
 */
public class ZoneLoadResult {

    public final List<DeliveryZone> deliveryZones;
    public final OriginDestinationMatrix odMatrix;
    public final CommodityRouter commodityRouter;
    public final TripGenerator tripGenerator;
    public final POIManager poiManager;
    public final GenerationAttractionBalancer gaBalancer;
    public final MetricsTracker metricsTracker;
    public final DestinationSelector destinationSelector;
    public final PointGenerator pointGenerator;
    public final TransportNetworkIndex networkIndex;

    public ZoneLoadResult(
            List<DeliveryZone> deliveryZones,
            OriginDestinationMatrix odMatrix,
            CommodityRouter commodityRouter,
            TripGenerator tripGenerator,
            POIManager poiManager,
            GenerationAttractionBalancer gaBalancer,
            MetricsTracker metricsTracker,
            DestinationSelector destinationSelector,
            PointGenerator pointGenerator,
            TransportNetworkIndex networkIndex) {
        this.deliveryZones = deliveryZones;
        this.odMatrix = odMatrix;
        this.commodityRouter = commodityRouter;
        this.tripGenerator = tripGenerator;
        this.poiManager = poiManager;
        this.gaBalancer = gaBalancer;
        this.metricsTracker = metricsTracker;
        this.destinationSelector = destinationSelector;
        this.pointGenerator = pointGenerator;
        this.networkIndex = networkIndex;
    }
}
