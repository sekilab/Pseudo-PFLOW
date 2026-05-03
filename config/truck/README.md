# Truck ABM Configuration

## Config Files

| File | Mode | Fleet | Description |
|------|------|-------|-------------|
| `truck_config.properties` | DUAL | 1,434,510 | **Master config** — 70 Kanto zones, A+ validated |
| `truck_config_5pct.properties` | DUAL | 71,726 | 5% scale test (~1 min runtime) |
| `truck_config_1pct.properties` | DUAL | 14,345 | 1% scale test (~15 sec runtime) |
| `truck_config_expanded.properties` | EXPANDED | 1,434,510 | 106 nationwide zones |
| `truck_config_expanded_5pct.properties` | EXPANDED | 71,726 | 5% scale of expanded |
| `truck_config_unified.properties` | UNIFIED | ~1,500,000 | 134 zones (Kanto + Osaka detail) |

**All variants inherit from the master config.** If you add a property to the master, also add it to all variants. Run `scripts/check_config_sync.sh` to detect drift.

## Subdirectories

### zones/
Zone definitions per simulation mode. CSV format: `zone_id,name,center_lon,center_lat,radius_km,warehouses_weight,...`

| File | Zones | Used By |
|------|-------|---------|
| `intra.csv` | 37 intra-metropolitan zones (Tokyo wards, etc.) | DUAL |
| `inter.csv` | 33 inter-metropolitan zones (Kanagawa, Saitama, etc.) | DUAL |
| `expanded.csv` | 106 zones (DUAL + 40 prefecture sub-zones) | EXPANDED |
| `unified.csv` | 134 zones (expanded + 30 Osaka zones) | UNIFIED |

### flows/
Origin-destination matrices. CSV format: rows=origin zones, columns=destination zones, values=trips/day.

| File | Dimensions | Source |
|------|-----------|--------|
| `od_volume.csv` | 65×65 | MFS File 08 (note: values are tonnage, not truck counts) |
| `od_volume_expanded.csv` | 106×106 | GDP-weighted disaggregation of MFS67-71 |
| `od_volume_unified.csv` | 134×134 | Merged Kanto + Osaka OD |

### facilities/
Point-of-Interest data for destination selection within zones.

- `logistics_*.csv` — Telepoint-sourced logistics POIs (Kanto only)
- `census_*.csv` — Economic Census-derived POIs (nationwide, 160K+ points)
- `industrial_sites.csv`, `port_terminals.csv`, `retail_shops.csv`, `shopping_malls.csv`

### operations/
Operational parameters calibrated from MFS data.

- `loading_constraints.csv` — Weight limit probabilities per commodity type
- `zone_cargo_gamma.csv` — Per-zone Gamma distribution parameters for cargo weight

### validation/
Automated test baselines.

- `mfs_baseline.csv` — 51 test metrics with targets, tolerances, and MFS source references

### overlays/osaka/
Osaka/Keihanshin-specific data for UNIFIED mode.

- `zones_osaka.csv`, `od_osaka.csv`, `ga_targets_osaka.csv`, `zone_mapping_osaka.csv`
