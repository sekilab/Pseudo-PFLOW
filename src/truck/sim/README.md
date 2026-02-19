# Tokyo Truck Simulation - Source Code Documentation

**Version**: 2.1 - Water Validation & POI Expansion
**Last Updated**: 2026-02-15
**Java Version**: 8+

---

## Overview

This package contains the complete agent-based model (ABM) for simulating truck freight movements across the Tokyo Metropolitan Area. The simulation models 1.43 million trucks operating across 73 MFS zones with realistic trip generation, destination selection, and cargo logistics.

---

## Package Structure

```
src/truck/sim/
├── core/                       # Core simulation engines
│   ├── FleetFactory.java      # Truck fleet initialization
│   ├── TripGenerationEngine.java  # Trip generation logic
│   └── ZoneInitializer.java   # Zone setup and configuration
│
├── util/                       # Utility classes
│   ├── CSVLoader.java         # CSV file loading utilities
│   └── DistanceCalculator.java  # Geographic distance calculations
│
├── interfaces/                 # (Reserved for future interfaces)
│
└── [Main Classes]             # Core simulation components
    ├── TruckSimulation.java   # Main simulation orchestrator (104 KB)
    ├── POIManager.java        # Point of Interest management (22 KB)
    ├── TruckAgent.java        # Individual truck agent (7.4 KB)
    ├── TruckTrip.java         # Trip data structure (8.8 KB)
    └── [See class index below]
```

---

## Class Index

### Main Orchestrator
- **`TruckSimulation.java`** (104 KB)
  - Main entry point and simulation orchestrator
  - Coordinates all components (fleet, zones, trips, metrics)
  - Implements water body validation (9 exclusion zones)
  - Manages dual-mode simulation (INTRA + INTER metropolitan)

### Agent & Trip Components
- **`TruckAgent.java`** (7.4 KB)
  - Individual truck agent with behavior modeling
  - Properties: type, capacity, home location, familiar area
  - Status tracking: loaded/empty, current location

- **`TruckTrip.java`** (8.8 KB)
  - Trip data structure (origin, destination, time, cargo)
  - Supports both delivery and empty return trips
  - Temporal data: departure time, duration, sim_day

- **`TruckType.java`** (2.1 KB)
  - Enum: DELIVERY, LONG_HAUL, MIXED_OPERATION
  - Defines truck operational patterns

- **`TruckStatus.java`** (786 bytes)
  - Enum: LOADED, EMPTY
  - Tracks cargo status

### POI & Destination Management
- **`POIManager.java`** (22 KB)
  - Manages 586+ Points of Interest (delivery destinations)
  - **Water validation**: Multi-layer filtering to prevent water destinations
  - POI selection with truck-type preferences
  - Commodity-aware routing

- **`POIManagerConstants.java`** (1.7 KB)
  - Constants for POI management

- **`PointOfInterest.java`** (3.5 KB)
  - POI data structure (coordinates, type, facility, zone)
  - Types: LOGISTIC_CENTER, RETAIL_SHOP, SHOPPING_MALL, INDUSTRIAL_SITE, PORT_TERMINAL

- **`FacilityType.java`** (3.5 KB)
  - Enum: LOGISTICS_HUB, LARGE_DISTRIBUTION, MEDIUM_WAREHOUSE, etc.
  - Maps to real-world facility types

### Zone & Geography
- **`DeliveryZone.java`** (9.4 KB)
  - MFS zone representation (73 zones total)
  - Properties: center coordinates, radius, POI list
  - Activity weights: warehouses, retail, construction, residential

- **`MetropolitanConfig.java`** (16 KB)
  - Metropolitan area configuration (Tokyo, Osaka, Nagoya)
  - Population, area, characteristics

### Trip Generation & Routing
- **`TripGenerator.java`** (9.8 KB)
  - Probabilistic trip generation using establishment data
  - Time-based trip generation patterns
  - Facility-based generation rates

- **`CommodityRouter.java`** (21 KB)
  - Commodity-based routing logic
  - 9 commodity types (agricultural, machinery, chemicals, etc.)
  - Time window constraints
  - Industry-specific flows

- **`OriginDestinationMatrix.java`** (18 KB)
  - O-D probability matrix (70×70 zones)
  - Loaded from MFS survey data (od_volume.csv)
  - Zone-to-zone trip distribution

- **`GenerationAttractionBalancer.java`** (14 KB)
  - G-A balance for realistic trip distribution
  - Prevents oversaturation of zones
  - Target-based trip allocation

### Configuration & Data Management
- **`TruckConfig.java`** (23 KB)
  - Configuration loader from truck_config.properties
  - Fleet size, simulation mode, file paths
  - Commodity routing settings

- **`TruckDataExporter.java`** (21 KB)
  - CSV export for trips, trucks, zone flows
  - **Includes sim_day column** for multi-day trip tracking
  - PFLOW format support

- **`TruckSimulationConstants.java`** (4.1 KB)
  - Global simulation constants
  - Default values and thresholds

### Validation & Metrics
- **`ValidationEngine.java`** (24 KB)
  - 51-metric validation suite
  - Compares against MLIT targets
  - Grade calculation (A+/A/B/C/D/F)

- **`ValidationTargets.java`** (6.2 KB)
  - Target values from MLIT and MFS data
  - Tolerance ranges for validation

- **`MetricsTracker.java`** (37 KB)
  - Real-time metrics collection
  - Fleet statistics, commodity mix, G-A balance
  - Performance monitoring

- **`SimulationMetrics.java`** (24 KB)
  - Metrics data structure
  - Aggregation and reporting

### Core Engines (core/ package)
- **`FleetFactory.java`**
  - Truck fleet initialization and assignment
  - Home location generation
  - Fleet mix (heavy/medium/small/light)

- **`TripGenerationEngine.java`**
  - Core trip generation algorithms
  - Stochastic trip timing
  - Load/empty trip pairing

- **`ZoneInitializer.java`**
  - Zone loading from CSV files
  - Zone validation and setup
  - Dual-mode zone merging (INTRA + INTER)

### Utilities (util/ package)
- **`CSVLoader.java`**
  - Generic CSV loading utilities
  - Error handling and validation

- **`DistanceCalculator.java`**
  - Haversine distance calculation
  - Geographic utilities

---

## Key Features

### Water Body Validation (v2.1)
**Implementation**: `TruckSimulation.java` (lines 1575-1593), `POIManager.java` (lines 53-77, 256-270, 320-350)

**Purpose**: Prevents trips to invalid water locations (Tokyo Bay, Pacific Ocean, rivers)

**Components**:
1. **Water Exclusion Zones** (9 zones defined):
   - Tokyo Bay (main body + inner harbors + Chiba coast)
   - Sagami Bay
   - Pacific Ocean (south and east)
   - Major rivers (Sumida, Tsurumi)

2. **Multi-Layer Validation**:
   - **Layer 1**: Load-time filtering in `POIManager.loadPOIFile()`
   - **Layer 2**: Selection filtering using Stream API in `selectPOIForINTRATrip()` and `selectPOIForTrip()`
   - **Layer 3**: Pre-use safety checks in `TruckSimulation` destination selection

3. **Port Exception**: PORT_TERMINAL type POIs allowed in water-adjacent areas

**Result**: Zero trips to water bodies (validated with 586 POIs)

### POI Expansion (v2.1)
**586 valid POIs** across 73 MFS zones:
- Logistics: 162 centers (+86% from baseline)
- Retail: 212 shops (+48%)
- Industrial: 120 sites (+67%)
- Shopping Malls: 57
- Port Terminals: 35

**Coverage**: All zones have POI coverage, improved diversity across Tokyo Metropolitan Area

### Temporal Support
**sim_day column**: Tracks trips spanning multiple simulation days
- Format: Integer day number (0, 1, 2, ...)
- starttime normalized to 0-86,399 seconds per day
- Exported in all CSV outputs via `TruckDataExporter`

---

## Data Flow

```
1. Configuration Loading
   TruckConfig.loadProperties() → truck_config.properties

2. Zone Initialization
   ZoneInitializer → zones/intra.csv + zones/inter.csv
                  → DeliveryZone objects (73 zones)

3. POI Loading
   POIManager.loadPOIsFromCSV() → facilities/*.csv
                                → Water validation
                                → 586 valid POIs

4. Fleet Initialization
   FleetFactory → TruckAgent objects (1.43M trucks)
               → Home location assignment
               → Fleet mix (heavy/medium/small/light)

5. O-D Matrix Loading
   OriginDestinationMatrix → flows/od_volume.csv
                          → 70×70 probability matrix

6. Trip Generation
   TripGenerationEngine → TruckTrip objects
                       → POI-based destination selection
                       → Water-validated coordinates
                       → Commodity assignment
                       → Time windows

7. G-A Balancing
   GenerationAttractionBalancer → flows/ga_targets.csv
                                → Zone capacity tracking
                                → Balanced trip distribution

8. Data Export
   TruckDataExporter → trips.csv (all trips)
                    → trips_pseudo_pflow.csv (with sim_day)
                    → trips_with_zones.csv (zone assignments)
                    → trucks.csv (fleet roster)

9. Validation
   ValidationEngine → 51 metrics checked
                   → Grade calculation (A+/A/B/C/D/F)
                   → Comparison vs MLIT targets
```

---

## Configuration Files

### Core Configuration
- `config/truck/truck_config.properties` - Main simulation settings
- `config/truck/zones/intra.csv` - 37 INTRA zones (Tokyo core)
- `config/truck/zones/inter.csv` - 33 INTER zones (extended Kanto)

### POI Data
- `config/truck/facilities/logistics.csv` - 178 entries (162 valid)
- `config/truck/facilities/retail.csv` - 226 entries (212 valid)
- `config/truck/facilities/industrial.csv` - 141 entries (120 valid)
- `config/truck/facilities/malls.csv` - 62 entries (57 valid)
- `config/truck/facilities/ports.csv` - 35 entries (35 valid)

### Flow & Target Data
- `config/truck/flows/od_volume.csv` - O-D matrix (MFS survey)
- `config/truck/flows/ga_targets.csv` - Generation-attraction targets
- `config/truck/flows/facility_flows.csv` - Facility type trip rates
- `config/truck/flows/industry_flows.csv` - Industry origin flows

### Operational Data
- `config/truck/operations/loading_rates.csv` - Loading time by commodity
- `config/truck/operations/loading_constraints.csv` - Loading constraints
- `config/truck/operations/time_windows.csv` - Delivery time windows

### Establishment Data
- `config/truck/facilities/est_subregion.csv` - Establishment density

---

## Usage

### Basic Simulation Run
```bash
# Compile
javac -source 8 -target 8 -d bin -sourcepath src src/truck/sim/TruckSimulation.java

# Run full scale (1.43M trucks)
java -Xmx16G -Xms8G -XX:+UseG1GC -cp bin truck.sim.TruckSimulation
```

### Test Scale Run
Edit `config/truck/truck_config.properties`:
```properties
truck.fleet.size=143451  # 10% scale
```

Then run:
```bash
java -Xmx4G -cp bin truck.sim.TruckSimulation
```

### Expected Output
```
[POI] Total POIs: 586
[CHECKPOINT] Initialized 1434510 trucks
[VALIDATION] Overall grade: A+ (51/51 tests passed)
```

Output files in `data/output/truck/run_YYYYMMDD_HHMMSS/`:
- `trips.csv` - All trips (468 MB for full scale)
- `trips_pseudo_pflow.csv` - PFLOW format with sim_day (350 MB)
- `trips_with_zones.csv` - With zone assignments (209 MB)
- `trucks.csv` - Fleet roster (173 MB)

---

## Development Guidelines

### Code Style
- **Java 8** compatible (source/target version 8)
- **Indentation**: 4 spaces
- **Line length**: Prefer <120 characters
- **Naming**: camelCase for methods/variables, PascalCase for classes
- **Comments**: JavaDoc for public methods, inline for complex logic

### Adding New Features

**1. Add New POI Type**:
```java
// 1. Update PointOfInterest.POIType enum
public enum POIType {
    LOGISTIC_CENTER, RETAIL_SHOP, SHOPPING_MALL, INDUSTRIAL_SITE, PORT_TERMINAL,
    YOUR_NEW_TYPE  // Add here
}

// 2. Add CSV loader in POIManager.loadPOIsFromCSV()
String yourTypePath = configDir + "your_type.csv";
List<PointOfInterest> yourPOIs = loadPOIFile(yourTypePath, POIType.YOUR_NEW_TYPE);

// 3. Add selection logic in POIManager.selectPOIForINTRATrip()
case YOUR_TRUCK_TYPE:
    candidates.addAll(yourTypePOIs);
    break;
```

**2. Add New Water Exclusion Zone**:
```java
// Edit TruckSimulation.java, WATER_EXCLUSION_ZONES array
private static final double[][] WATER_EXCLUSION_ZONES = {
    // ... existing zones ...
    {minLon, minLat, maxLon, maxLat},  // Your new zone
};
```

**3. Add New Validation Metric**:
```java
// 1. Add target in ValidationTargets.java
public static final double YOUR_METRIC_TARGET = 12345.0;

// 2. Add check in ValidationEngine.java validate() method
addCheck("Your Metric Description", actualValue, target, tolerance);
```

### Testing Guidelines

**Unit Testing**:
- Test individual components in isolation
- Verify water validation logic
- Check POI selection randomness
- Validate G-A balance

**Integration Testing**:
- Run 10% scale test (143K trucks)
- Verify all 51 validation metrics pass
- Check output file formats
- Validate zero water destinations

**Performance Testing**:
- Full scale (1.43M trucks) should complete in <4 minutes
- Memory usage should stay under 16 GB
- No memory leaks over extended runs

---

## Troubleshooting

### Common Issues

**1. OutOfMemoryError**
```bash
# Increase heap size
java -Xmx20G -Xms10G -XX:+UseG1GC -cp bin truck.sim.TruckSimulation
```

**2. POIs Loading with Water Warning**
```
[POI] Skipped LC001 (Tokyo Port) - coordinates in water body
```
**Status**: This is expected behavior - water validation is working correctly.

**3. Validation Grade < A+**
- Check `validation_summary.txt` for failed metrics
- Verify configuration files are up to date
- Ensure O-D matrix loaded correctly
- Check G-A balance targets

**4. Zero Trips Generated**
- Verify POI files exist and load successfully
- Check zone CSV files are valid
- Ensure O-D matrix is not empty
- Validate establishment data loaded

---

## Performance Benchmarks

### Full Scale (1.43M trucks, 586 POIs)
- **Runtime**: ~3 minutes
- **Memory Peak**: ~12 GB
- **Throughput**: ~478,000 trucks/minute
- **Output Size**: 1.2 GB
- **Validation Grade**: A+ (51/51)

### Test Scale (143K trucks, 586 POIs)
- **Runtime**: ~45 seconds
- **Memory Peak**: ~3 GB
- **Output Size**: 120 MB

---

## Version History

### v2.1 (2026-02-15) - Water Validation & POI Expansion
- ✅ Added comprehensive water body validation (9 exclusion zones)
- ✅ Expanded POI dataset from 381 to 586 valid POIs (+54%)
- ✅ Fixed 6 zone centers from water to land
- ✅ Multi-layer water filtering (load/select/use)
- ✅ Port exception handling for water-adjacent facilities
- ✅ All 51 validation metrics passing (Grade A+)

### v2.0 (2026-02-14) - Multi-Day Temporal Support
- ✅ Added sim_day column to CSV outputs
- ✅ Normalized starttime to 0-86,399 seconds per day
- ✅ Scaled fleet to full MLIT (1.43M trucks)
- ✅ Implemented dual-mode simulation (INTRA+INTER)

### v1.0 (2026-01) - Initial Implementation
- ✅ Basic agent-based truck simulation
- ✅ POI-based destination selection
- ✅ O-D matrix integration
- ✅ G-A balancing
- ✅ Validation engine

---

## Contributors

**Lead Development**: Tokyo Truck ABM Framework Team
**Water Validation & POI Expansion**: Claude Sonnet 4.5 (2026-02-15)
**Initial Framework**: Truck ABM Research Group

---

## References

### Data Sources
- **MLIT**: Ministry of Land, Infrastructure, Transport and Tourism (freight statistics)
- **MFS**: Metropolitan Freight Survey (Tokyo area O-D data)
- **Establishment Data**: Tokyo Metropolitan Government statistics
- **POI Data**: Real-world facility locations (2024-2025)

### Related Documentation
- `WATER_VALIDATION_POI_EXPANSION_REPORT.md` - Full implementation report
- `WATER_VALIDATION_QUICK_START.md` - Quick reference guide
- `FINAL_VALIDATION_RESULTS.md` - Latest validation results
- `IMPLEMENTATION_COMPLETE.md` - Executive summary

---

## License

[Your license information here]

---

**Last Updated**: February 15, 2026
**Status**: Production Ready (Grade A+ validation)
**Contact**: [Your contact information]
