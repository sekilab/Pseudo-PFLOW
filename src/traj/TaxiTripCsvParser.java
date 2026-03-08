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
 * Parses taxi trips_pseudo_pflow.csv (16 columns) into TaxiTripRecords
 * and converts them to PFLOW Person+Trip objects for routing.
 * <p>
 * CSV columns (from TaxiDataExporter.exportTripsPseudoPFlow):
 *   0:id, 1:starttime, 2:start_lon, 3:start_lat, 4:end_lon, 5:end_lat,
 *   6:transport_mode(8), 7:purpose(0/3), 8:occupation,
 *   9:passenger_in, 10:taxi_id, 11:distance_km, 12:fare_yen,
 *   13:is_night_trip, 14:starttime_h, 15:simulation_day
 */
public class TaxiTripCsvParser implements VehicleTripCsvParser<TaxiTripRecord> {

    @Override
    public Map<Integer, List<TaxiTripRecord>> parseTripRecords(String csvPath) {
        Map<Integer, List<TaxiTripRecord>> taxiTrips = new TreeMap<>();
        int lineCount = 0;
        int parseErrors = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();  // skip header
            if (header == null) {
                System.err.println("[TAXI PARSER] Empty CSV file: " + csvPath);
                return Collections.emptyMap();
            }

            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;
                try {
                    TaxiTripRecord record = parseLine(line);
                    taxiTrips.computeIfAbsent(record.taxiId, k -> new ArrayList<>()).add(record);
                } catch (Exception e) {
                    parseErrors++;
                    if (parseErrors <= 5) {
                        System.err.printf("[TAXI PARSER] Parse error at line %d: %s%n", lineCount + 1, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("[TAXI PARSER] Failed to read %s: %s%n", csvPath, e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }

        // Sort each taxi's trips by departure time
        for (List<TaxiTripRecord> records : taxiTrips.values()) {
            records.sort(Comparator.comparingLong(r -> r.depTime));
        }

        System.out.printf("[TAXI PARSER] Parsed %,d trips for %,d taxis (%d parse errors)%n",
                lineCount, taxiTrips.size(), parseErrors);
        return taxiTrips;
    }

    @Override
    public List<Person> convertToPersons(Map<Integer, List<TaxiTripRecord>> recordsByVehicle) {
        List<Person> persons = new ArrayList<>(recordsByVehicle.size());
        int totalTrips = 0;

        for (Map.Entry<Integer, List<TaxiTripRecord>> entry : recordsByVehicle.entrySet()) {
            int taxiId = entry.getKey();
            List<TaxiTripRecord> records = entry.getValue();

            // Create Person (taxi becomes a "person" for PFLOW routing)
            Person person = new Person(null, taxiId, 0, EGender.MALE, ELabor.WORKER);

            for (TaxiTripRecord r : records) {
                // ETransport.CAR -> DrmTransport.VEHICLE for road routing
                // passenger trips -> FREE (leisure/shopping), empty -> BUSINESS (operational)
                EPurpose purpose = r.passengerIn ? EPurpose.FREE : EPurpose.BUSINESS;
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

        System.out.printf("[TAXI PARSER] Created %,d Person objects with %,d total trips%n",
                persons.size(), totalTrips);
        return persons;
    }

    /**
     * Parse one CSV line into a TaxiTripRecord.
     * Column indices match TaxiDataExporter.exportTripsPseudoPFlow() output.
     */
    static TaxiTripRecord parseLine(String line) {
        String[] cols = line.split(",");

        long tripId = Long.parseLong(cols[0].trim());
        int starttime = Integer.parseInt(cols[1].trim());
        double originLon = Double.parseDouble(cols[2].trim());
        double originLat = Double.parseDouble(cols[3].trim());
        double destLon = Double.parseDouble(cols[4].trim());
        double destLat = Double.parseDouble(cols[5].trim());
        // cols[6] = transport_mode (always 8)
        // cols[7] = purpose (0 or 3)
        // cols[8] = occupation (always 0)
        boolean passengerIn = Boolean.parseBoolean(cols[9].trim());
        int taxiId = Integer.parseInt(cols[10].trim());
        double distanceKm = Double.parseDouble(cols[11].trim());
        double fareYen = Double.parseDouble(cols[12].trim());
        boolean isNightTrip = Boolean.parseBoolean(cols[13].trim());
        // cols[14] = starttime_h (human-readable, skip)
        int simDay = Integer.parseInt(cols[15].trim());

        long depTime = (long) simDay * 86400L + starttime;

        return new TaxiTripRecord(
                tripId, taxiId, depTime,
                originLon, originLat, destLon, destLat,
                distanceKm, passengerIn, fareYen, isNightTrip, simDay
        );
    }
}
