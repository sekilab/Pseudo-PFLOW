#!/bin/bash
# Post-audit full-pipeline smoke test.
# Truck EXPANDED (100%) -> Taxi Tokyo+Osaka -> Full-geometry trajectories.
# Uses bin_fresh (Java 21 compiled with audit edits), not bin (Java 25).

set -uo pipefail
cd "$(dirname "$0")"

JAVA="/c/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot/bin/java.exe"
M2="C:/Users/north/.m2/repository"

# Windows classpath uses ';'
CP="bin_fresh"
CP="${CP};lib/pflowlib.jar"
CP="${CP};${M2}/org/locationtech/jts/jts-core/1.16.0/jts-core-1.16.0.jar"
CP="${CP};${M2}/com/vividsolutions/jts/1.13/jts-1.13.jar"
CP="${CP};${M2}/org/geotools/gt-api/20.1/gt-api-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-data/20.1/gt-data-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-main/20.1/gt-main-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-metadata/20.1/gt-metadata-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-opengis/20.1/gt-opengis-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-referencing/20.1/gt-referencing-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-shapefile/20.1/gt-shapefile-20.1.jar"
CP="${CP};${M2}/org/geotools/gt-epsg-hsql/20.1/gt-epsg-hsql-20.1.jar"
CP="${CP};${M2}/org/hsqldb/hsqldb/2.4.1/hsqldb-2.4.1.jar"
CP="${CP};${M2}/commons-pool/commons-pool/1.5.4/commons-pool-1.5.4.jar"
CP="${CP};${M2}/commons-lang/commons-lang/2.6/commons-lang-2.6.jar"
CP="${CP};${M2}/javax/measure/unit-api/1.0/unit-api-1.0.jar"
CP="${CP};${M2}/si/uom/si-quantity/0.7.1/si-quantity-0.7.1.jar"
CP="${CP};${M2}/si/uom/si-units-java8/0.7.1/si-units-java8-0.7.1.jar"
CP="${CP};${M2}/systems/uom/systems-common-java8/0.7.2/systems-common-java8-0.7.2.jar"
CP="${CP};${M2}/tec/uom/uom-se/1.0.8/uom-se-1.0.8.jar"
CP="${CP};${M2}/tec/uom/lib/uom-lib-common/1.0.2/uom-lib-common-1.0.2.jar"
CP="${CP};${M2}/net/java/dev/jsr-275/jsr-275/1.0-beta-2/jsr-275-1.0-beta-2.jar"
CP="${CP};${M2}/org/ejml/ejml-core/0.34/ejml-core-0.34.jar"
CP="${CP};${M2}/org/ejml/ejml-ddense/0.34/ejml-ddense-0.34.jar"

NET="../data/network"
ALL_PREFS="1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47"
TOKYO_PREFS="8,9,10,11,12,13,14"
OSAKA_PREFS="24,25,26,27,28,29,30"
MAX_RC=9
LOG_DIR="logs/audit_pipeline_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$LOG_DIR"

section() { echo; echo "======== $1 ========"; date; }

latest_run() {
  ls -td "$1"/run_* 2>/dev/null | head -n1
}

# --- Step 1: Truck EXPANDED (100% fleet) ---
section "STEP 1/6: Truck EXPANDED sim"
"$JAVA" -Xmx16G -cp "$CP" truck.sim.TruckSimulation \
    config/truck/truck_config_expanded.properties \
    2>&1 | tee "$LOG_DIR/01_truck.log"
TRUCK_RUN=$(latest_run "output/trips/truck")
TRUCK_CSV="$TRUCK_RUN/trips_pseudo_pflow.csv"
echo "[driver] truck trips: $TRUCK_CSV"
[ -s "$TRUCK_CSV" ] || { echo "[driver] FATAL: truck trips missing"; exit 1; }

# --- Step 2: Taxi Tokyo ---
section "STEP 2/6: Taxi Tokyo sim"
"$JAVA" -Xmx6G -cp "$CP" taxi.sim.TaxiSimulation \
    config/taxi/tokyo/taxi_config.properties \
    2>&1 | tee "$LOG_DIR/02_taxi_tokyo.log"
TOKYO_RUN=$(latest_run "output/trips/taxi/tokyo")
TOKYO_CSV="$TOKYO_RUN/trips_pseudo_pflow.csv"
echo "[driver] tokyo trips: $TOKYO_CSV"
[ -s "$TOKYO_CSV" ] || { echo "[driver] FATAL: tokyo trips missing"; exit 1; }

# --- Step 3: Taxi Osaka ---
section "STEP 3/6: Taxi Osaka sim"
"$JAVA" -Xmx6G -cp "$CP" taxi.sim.TaxiSimulation \
    config/taxi/osaka/taxi_config.properties \
    2>&1 | tee "$LOG_DIR/03_taxi_osaka.log"
OSAKA_RUN=$(latest_run "output/trips/taxi/osaka")
OSAKA_CSV="$OSAKA_RUN/trips_pseudo_pflow.csv"
echo "[driver] osaka trips: $OSAKA_CSV"
[ -s "$OSAKA_CSV" ] || { echo "[driver] FATAL: osaka trips missing"; exit 1; }

# --- Step 4: Truck trajectory, FULL mode, all 47 prefs ---
section "STEP 4/6: Truck trajectory FULL (all 47 prefs)"
"$JAVA" -Xmx56G -XX:+UseG1GC -cp "$CP" traj.TrajectoryMain \
    truck "$TRUCK_CSV" "$NET" "output/trajectory/truck" "$ALL_PREFS" "$MAX_RC" full \
    2>&1 | tee "$LOG_DIR/04_traj_truck.log"

# --- Step 5: Tokyo taxi trajectory, FULL mode ---
section "STEP 5/6: Tokyo taxi trajectory FULL (prefs 8-14)"
"$JAVA" -Xmx56G -XX:+UseG1GC -cp "$CP" traj.TrajectoryMain \
    taxi "$TOKYO_CSV" "$NET" "output/trajectory/taxi/tokyo" "$TOKYO_PREFS" "$MAX_RC" full \
    2>&1 | tee "$LOG_DIR/05_traj_tokyo.log"

# --- Step 6: Osaka taxi trajectory, FULL mode ---
section "STEP 6/6: Osaka taxi trajectory FULL (prefs 24-30)"
"$JAVA" -Xmx56G -XX:+UseG1GC -cp "$CP" traj.TrajectoryMain \
    taxi "$OSAKA_CSV" "$NET" "output/trajectory/taxi/osaka" "$OSAKA_PREFS" "$MAX_RC" full \
    2>&1 | tee "$LOG_DIR/06_traj_osaka.log"

section "PIPELINE DONE"
echo "Logs in: $LOG_DIR"
