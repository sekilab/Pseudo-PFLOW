# Taxi ABM Configuration

## Per-City Structure

Each city has its own directory with a `taxi_config.properties` file:

```
config/taxi/
├── tokyo/taxi_config.properties      # 36,800 taxis
├── osaka/taxi_config.properties      # 17,160 taxis  
├── nagoya/taxi_config.properties     # 9,240 taxis
├── kanagawa/taxi_config.properties   # 9,625 taxis
├── yokohama/taxi_config.properties   # 6,630 taxis
├── kyoto/taxi_config.properties      # 5,700 taxis
├── chiba/taxi_config.properties      # 4,940 taxis
├── saitama/taxi_config.properties    # 4,129 taxis
├── kobe/taxi_config.properties       # 4,774 taxis
└── shizuoka/taxi_config.properties   # 3,269 taxis
```

## Key Parameters Per City

Each config specifies: fleet size, trips/taxi target, fare structure (base fare, per-km rate, night surcharge), city center coordinates, hotspot locations (airports, major stations), average speed, and demand zone definitions.

## Data Sources

- Fleet sizes: Taxi association statistics per city
- Fare tariffs: THTA (Tokyo), city associations, taxi-calculator.com
- Spatial validation: Shared shapefiles in `src/shared/gm-jp/`

## Adding a New City

1. Create `config/taxi/{cityname}/taxi_config.properties`
2. Copy from an existing city config as template
3. Update: fleet size, coordinates, hotspots, fare structure, speed
4. Run: `./scripts/run_taxi.sh config/taxi/{cityname}/taxi_config.properties`
