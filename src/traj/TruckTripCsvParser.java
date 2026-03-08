package traj;

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
 * Parses truck trips_pseudo_pflow.csv (19 columns) into TruckTripRecords
 * and converts them to PFLOW Person+Trip objects for routing.
 * <p>
 * Extracted from {@code truck.traj.TruckTripConverter} with unified interface.
 * <p>
 * CSV columns:
 *   0:id, 1:sim_day, 2:starttime, 3:start_lon, 4:start_lat,
 *   5:end_lon, 6:end_lat, 7:transport_mode(9), 8:purpose(0/1),
 *   9:occupation, 10:cargo_loaded, 11:truck_id, 12:distance_km,
 *   13:cargo_weight_tons, 14:goods_type, 15:vehicle_size,
 *   16:capacity_tons, 17:status, 18:starttime_h
 */
public class TruckTripCsvParser implements VehicleTripCsvParser<TruckTripRecord> {

    @Override
    public Map<Integer, List<TruckTripRecord>> parseTripRecords(String csvPath) {
        Map<Integer, List<TruckTripRecord>> truckTrips = new TreeMap<>();
        int lineCount = 0;
        int parseErrors = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();  // skip header
            if (header == null) {
                System.err.println("[TRUCK PARSER] Empty CSV file: " + csvPath);
                return Collections.emptyMap();
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
                        System.err.printf("[TRUCK PARSER] Parse error at line %d: %s%n", lineCount + 1, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("[TRUCK PARSER] Failed to read %s: %s%n", csvPath, e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }

        // Sort each truck's trips by departure time
        for (List<TruckTripRecord> records : truckTrips.values()) {
            records.sort(Comparator.comparingLong(r -> r.depTime));
        }

        System.out.printf("[TRUCK PARSER] Parsed %,d trips for %,d trucks (%d parse errors)%n",
                lineCount, truckTrips.size(), parseErrors);
        return truckTrips;
    }

    @Override
    public List<Person> convertToPersons(Map<Integer, List<TruckTripRecord>> recordsByVehicle) {
        List<Person> persons = new ArrayList<>(recordsByVehicle.size());
        int totalTrips = 0;

        for (Map.Entry<Integer, List<TruckTripRecord>> entry : recordsByVehicle.entrySet()) {
            int truckId = entry.getKey();
            List<TruckTripRecord> records = entry.getValue();

            // Create Person (truck becomes a "person" for PFLOW routing)
            Person person = new Person(null, truckId, 0, EGender.MALE, ELabor.WORKER);

            for (TruckTripRecord r : records) {
                // ETransport.CAR -> DrmTransport.VEHICLE for road routing
                EPurpose purpose = r.cargoLoaded ? EPurpose.BUSINESS : EPurpose.FREE;
                Trip trip = new Trip(
                        ETransport.CAR, purpose, r.depTime,
                        new LonLat(r.originLon, r.originLat),
                        new LonLat(r.destLon, r.destLat)
                );
                person.addTrip(trip);
                totalTrips++;
            }

            persons.add(person);
        }

        System.out.printf("[TRUCK PARSER] Created %,d Person objects with %,d total trips%n",
                persons.size(), totalTrips);
        return persons;
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
        boolean cargoLoaded = Boolean.parseBoolean(cols[10]);
        int truckId = Integer.parseInt(cols[11]);
        double distanceKm = Double.parseDouble(cols[12]);
        String goodsType = cols[14];
        String vehicleSize = cols[15];

        long depTime = (long) simDay * 86400L + starttime;

        return new TruckTripRecord(
                tripId, truckId, depTime,
                originLon, originLat, destLon, destLat,
                cargoLoaded, goodsType, vehicleSize, distanceKm
        );
    }
}
