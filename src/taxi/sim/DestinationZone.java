package taxi.sim;

/**
 * Represents a destination zone with time-dependent attractiveness
 *
 * Each zone has characteristics that make it attractive at different times:
 * - Office/business districts: high attractiveness in daytime
 * - Nightlife/entertainment areas: high attractiveness at night
 * - Residential areas: high attractiveness in morning/evening (commute times)
 *
 * V4.0: Added zone type classification and transport hub identification
 */
public class DestinationZone {
    private final String zoneId;
    private final String zoneType;          // V4.0: business / residential / commercial / mixed / industrial
    private final String name;
    private final double centerLon;
    private final double centerLat;
    private final double radiusKm;

    // Time-dependent attractiveness weights
    private final double jobsWeight;         // Office/business attractiveness
    private final double shopsWeight;        // Shopping/retail attractiveness
    private final double nightlifeWeight;    // Entertainment/nightlife attractiveness
    private final double residentialWeight;  // Residential area attractiveness

    // V4.0: Transport hub identification
    private final boolean isTransportHub;    // true for airports, major stations

    // V5.0: Transport proximity metadata (enriched from shapefiles at initialization)
    private double nearestStationDistKm = Double.MAX_VALUE;
    private String nearestStationName = null;
    private int stationsWithin1km = 0;
    private double nearestAirportDistKm = Double.MAX_VALUE;
    private String nearestAirportName = null;

    /**
     * Constructor for DestinationZone (V4.0 with zone type and transport hub)
     */
    public DestinationZone(String zoneId, String zoneType, String name, double lon, double lat, double radius,
                           double jobs, double shops, double nightlife, double residential,
                           boolean isTransportHub) {
        this.zoneId = zoneId;
        this.zoneType = zoneType;
        this.name = name;
        this.centerLon = lon;
        this.centerLat = lat;
        this.radiusKm = radius;
        this.jobsWeight = jobs;
        this.shopsWeight = shops;
        this.nightlifeWeight = nightlife;
        this.residentialWeight = residential;
        this.isTransportHub = isTransportHub;
    }

    /**
     * Calculate attractiveness score for a given time period
     *
     * @param timePeriod 0=daytime (6-18), 1=evening (18-24), 2=night (0-6)
     * @param beta1 coefficient for jobs
     * @param beta2 coefficient for shops
     * @param beta3 coefficient for nightlife
     * @param beta4 coefficient for residential
     * @return attractiveness score
     */
    public double calculateAttractiveness(int timePeriod, double beta1, double beta2,
                                          double beta3, double beta4) {
        // Base attractiveness from zone characteristics
        double baseScore = beta1 * jobsWeight +
                          beta2 * shopsWeight +
                          beta3 * nightlifeWeight +
                          beta4 * residentialWeight;

        // Time-dependent multipliers
        double timeMultiplier = 1.0;

        switch (timePeriod) {
            case 0: // Daytime (06:00-18:00) - residential and office areas high
                if (residentialWeight > 0.5 || jobsWeight > 0.5) {
                    timeMultiplier = 1.5;
                }
                break;

            case 1: // Evening (18:00-00:00) - business/shopping districts high
                if (shopsWeight > 0.5 || jobsWeight > 0.5) {
                    timeMultiplier = 1.5;
                }
                break;

            case 2: // Night (00:00-06:00) - entertainment/nightlife and residential high
                // After trains stop
                if (nightlifeWeight > 0.5) {
                    timeMultiplier = 2.0;
                } else if (residentialWeight > 0.5) {
                    timeMultiplier = 1.5;
                }
                break;
        }

        // V5.0: Station proximity boost — zones near stations generate more taxi demand
        double transportBoost = getStationProximityBoost();

        // Multi-station bonus — zones with many stations (interchange hubs) get extra boost
        if (stationsWithin1km >= 3) {
            transportBoost *= 1.3;  // Major interchange
        } else if (stationsWithin1km >= 2) {
            transportBoost *= 1.15; // Double station
        }

        return baseScore * timeMultiplier * transportBoost;
    }

    /**
     * Check if a point is within this zone
     */
    public boolean containsPoint(double lon, double lat) {
        double distance = calculateDistance(lon, lat, centerLon, centerLat);
        return distance <= radiusKm;
    }

    /**
     * Calculate distance from zone center to a point
     */
    public double getDistanceFromCenter(double lon, double lat) {
        return calculateDistance(lon, lat, centerLon, centerLat);
    }

    /**
     * Haversine distance calculation — pure straight-line, no Manhattan factor.
     *
     * <p>TX7 (2026-04-22): Intentionally geometric, unlike {@link TaxiTrip#distanceKm}
     * which applies the Manhattan factor. Zone radius, {@link #containsPoint},
     * and the enrichment-dedup check all ask "how close in space is this
     * point?" — a geometric question. Trip distance asks "how far does the
     * driver travel?" — a routing question, where Manhattan ≈ road-network
     * detour. Mixing the two was flagged as a consistency bug; the resolution
     * is to keep them semantically distinct and document it here.
     *
     * <p>Earth radius is sourced from {@link TaxiConfig} so both methods agree
     * on the one planetary constant that IS shared.
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final double EARTH_RADIUS_KM = TaxiConfig.getInstance().getEarthRadiusKm();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    // Getters
    public String getZoneId() { return zoneId; }
    public String getZoneType() { return zoneType; }  // V4.0
    public String getName() { return name; }
    public double getCenterLon() { return centerLon; }
    public double getCenterLat() { return centerLat; }
    public double getRadiusKm() { return radiusKm; }
    public double getJobsWeight() { return jobsWeight; }
    public double getShopsWeight() { return shopsWeight; }
    public double getNightlifeWeight() { return nightlifeWeight; }
    public double getResidentialWeight() { return residentialWeight; }
    public boolean isTransportHub() { return isTransportHub; }  // V4.0

    // V5.0: Transport proximity setters (called during zone enrichment)
    public void setNearestStation(double distKm, String name) {
        this.nearestStationDistKm = distKm;
        this.nearestStationName = name;
    }

    public void setStationsWithin1km(int count) {
        this.stationsWithin1km = count;
    }

    public void setNearestAirport(double distKm, String name) {
        this.nearestAirportDistKm = distKm;
        this.nearestAirportName = name;
    }

    // V5.0: Transport proximity getters
    public double getNearestStationDistKm() { return nearestStationDistKm; }
    public String getNearestStationName() { return nearestStationName; }
    public int getStationsWithin1km() { return stationsWithin1km; }
    public double getNearestAirportDistKm() { return nearestAirportDistKm; }
    public String getNearestAirportName() { return nearestAirportName; }

    /** @return true if a railway station is within 2km of zone center */
    public boolean isNearStation() { return nearestStationDistKm < 2.0; }

    /** @return true if an airport is within 5km of zone center */
    public boolean isNearAirport() { return nearestAirportDistKm < 5.0; }

    /**
     * V5.0: Station proximity boost for attractiveness calculation.
     * Exponential decay: ~2.0× at 0km, ~1.4× at 0.5km, ~1.0× at 2km+
     *
     * @return Multiplicative boost factor (1.0 = no boost)
     */
    public double getStationProximityBoost() {
        if (nearestStationDistKm >= 2.0) return 1.0;
        return 1.0 + Math.exp(-nearestStationDistKm / 0.5);
    }

    @Override
    public String toString() {
        return String.format("Zone[%s: %s at (%.4f,%.4f) r=%.1fkm]",
            zoneId, name, centerLon, centerLat, radiusKm);
    }
}
