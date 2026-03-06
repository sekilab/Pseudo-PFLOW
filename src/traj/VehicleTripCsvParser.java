package traj;

import java.util.List;
import java.util.Map;
import pseudo.res.Person;

/**
 * Strategy interface for parsing vehicle-type-specific CSV formats
 * and converting them into PFLOW routing objects.
 * <p>
 * Each vehicle type has a different trips_pseudo_pflow.csv column layout.
 * Implementations handle the parsing details while producing a common
 * structure for the routing engine.
 *
 * @param <R> concrete trip record type (e.g., TruckTripRecord, TaxiTripRecord)
 */
public interface VehicleTripCsvParser<R extends VehicleTripRecord> {

    /**
     * Parse a trips_pseudo_pflow.csv file into trip records grouped by vehicle ID.
     * Records within each vehicle are sorted by departure time.
     *
     * @param csvPath path to trips_pseudo_pflow.csv
     * @return map of vehicleId -> sorted list of trip records
     */
    Map<Integer, List<R>> parseTripRecords(String csvPath);

    /**
     * Convert parsed trip records into PFLOW Person+Trip objects for A* routing.
     * Each vehicle becomes a Person; each trip becomes a Trip with ETransport.CAR.
     *
     * @param recordsByVehicle records grouped by vehicle ID (from parseTripRecords)
     * @return list of Person objects ready for routing
     */
    List<Person> convertToPersons(Map<Integer, List<R>> recordsByVehicle);
}
