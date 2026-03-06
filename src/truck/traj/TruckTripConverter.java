package truck.traj;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import jp.ac.ut.csis.pflow.geom2.LonLat;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EPurpose;
import pseudo.res.ETransport;
import pseudo.res.Person;
import pseudo.res.Trip;

/**
 * Converts truck simulation output (trips_pseudo_pflow.csv) into PFLOW
 * Person + Trip objects for Dijkstra trajectory routing.
 * <p>
 * trips_pseudo_pflow.csv columns (19):
 *   0:id, 1:sim_day, 2:starttime, 3:start_lon, 4:start_lat,
 *   5:end_lon, 6:end_lat, 7:transport_mode(9), 8:purpose(0/1),
 *   9:occupation, 10:cargo_loaded, 11:truck_id, 12:distance_km,
 *   13:cargo_weight_tons, 14:goods_type, 15:vehicle_size,
 *   16:capacity_tons, 17:status, 18:starttime_h
 * <p>
 * Mapping:
 *   truck_id    -> Person.id
 *   ETransport  -> CAR(3) for routing (maps to DrmTransport.VEHICLE)
 *   EPurpose    -> BUSINESS(500) for delivery, FREE(400) for empty
 *   depTime     -> sim_day * 86400 + starttime (seconds)
 */
public class TruckTripConverter {

    /**
     * Load truck trips CSV and convert to PFLOW Person+Trip objects.
     * Trips are grouped by truck_id and sorted by departure time.
     *
     * @param csvPath path to trips_pseudo_pflow.csv
     * @return list of Person objects, each containing their trips
     */
    public static List<Person> loadTruckTrips(String csvPath) {
        // Phase 1: Parse all records
        Map<Integer, List<TruckTripRecord>> truckTrips = new TreeMap<>();
        int lineCount = 0;
        int parseErrors = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();  // skip header
            if (header == null) {
                System.err.println("[CONVERTER] Empty CSV file: " + csvPath);
                return Collections.emptyList();
            }

            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;
                try {
                    TruckTripRecord record = parseLine(line);
                    truckTrips.computeIfAbsent(record.truckId, k -> new ArrayList<>()).add(record);
                } catch (Exception e) {
                    parseErrors++;
                    if (parseErrors <= 5) {
                        System.err.printf("[CONVERTER] Parse error at line %d: %s%n", lineCount + 1, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("[CONVERTER] Failed to read %s: %s%n", csvPath, e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }

        System.out.printf("[CONVERTER] Parsed %,d trips for %,d trucks (%d parse errors)%n",
                lineCount, truckTrips.size(), parseErrors);

        // Phase 2: Convert to Person + Trip objects
        List<Person> persons = new ArrayList<>(truckTrips.size());
        int totalTrips = 0;

        for (Map.Entry<Integer, List<TruckTripRecord>> entry : truckTrips.entrySet()) {
            int truckId = entry.getKey();
            List<TruckTripRecord> records = entry.getValue();

            // Sort trips by departure time (ensures correct chaining)
            records.sort(Comparator.comparingLong(r -> r.depTime));

            // Create Person (truck becomes a "person" for PFLOW routing)
            Person person = new Person(null, truckId, 0, EGender.MALE, ELabor.WORKER);

            for (TruckTripRecord r : records) {
                // ETransport.CAR -> DrmTransport.VEHICLE for road routing
                // EPurpose: BUSINESS for delivery (cargo_loaded), FREE for empty
                EPurpose purpose = r.cargoLoaded ? EPurpose.BUSINESS : EPurpose.FREE;

                Trip trip = new Trip(
                        ETransport.CAR,
                        purpose,
                        r.depTime,
                        new LonLat(r.originLon, r.originLat),
                        new LonLat(r.destLon, r.destLat)
                );
                person.addTrip(trip);
                totalTrips++;
            }

            persons.add(person);
        }

        System.out.printf("[CONVERTER] Created %,d Person objects with %,d total trips%n",
                persons.size(), totalTrips);
        return persons;
    }

    /**
     * Get raw TruckTripRecords grouped by truck ID (for output with truck-specific fields).
     * Useful when trajectory writer needs goods_type, vehicle_size, etc.
     *
     * @param csvPath path to trips_pseudo_pflow.csv
     * @return map of truckId -> sorted list of TruckTripRecords
     */
    public static Map<Integer, List<TruckTripRecord>> loadTruckTripRecords(String csvPath) {
        Map<Integer, List<TruckTripRecord>> truckTrips = new TreeMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            br.readLine();  // skip header
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    TruckTripRecord record = parseLine(line);
                    truckTrips.computeIfAbsent(record.truckId, k -> new ArrayList<>()).add(record);
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Sort each truck's trips by departure time
        for (List<TruckTripRecord> records : truckTrips.values()) {
            records.sort(Comparator.comparingLong(r -> r.depTime));
        }

        return truckTrips;
    }

    /**
     * Parse one CSV line into a TruckTripRecord.
     * Column indices match TruckDataExporter.exportTripsPseudoPFlow() output.
     */
    static TruckTripRecord parseLine(String line) {
        String[] cols = line.split(",");

        long tripId = Long.parseLong(cols[0]);
        int simDay = Integer.parseInt(cols[1]);
        int starttime = Integer.parseInt(cols[2]);
        double originLon = Double.parseDouble(cols[3]);
        double originLat = Double.parseDouble(cols[4]);
        double destLon = Double.parseDouble(cols[5]);
        double destLat = Double.parseDouble(cols[6]);
        // cols[7] = transport_mode (always 9)
        // cols[8] = purpose (0 or 1)
        // cols[9] = occupation (always 0)
        boolean cargoLoaded = Boolean.parseBoolean(cols[10]);
        int truckId = Integer.parseInt(cols[11]);
        double distanceKm = Double.parseDouble(cols[12]);
        // cols[13] = cargo_weight_tons
        String goodsType = cols[14];
        String vehicleSize = cols[15];
        // cols[16] = capacity_tons
        // cols[17] = status
        // cols[18] = starttime_h

        long depTime = (long) simDay * 86400L + starttime;

        return new TruckTripRecord(
                tripId, truckId, depTime,
                originLon, originLat, destLon, destLat,
                cargoLoaded, goodsType, vehicleSize, distanceKm
        );
    }
}
