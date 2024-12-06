package gtfs;

import org.onebusaway.gtfs.model.FareAttribute;

import java.util.List;

public class FareCalculator {

    public static double calculateFare(Trip trip, List<FareRule> fareRules, List<Fare> fareAttributes, Stop originStop, Stop destinationStop) {
        String applicableFareId = null;

        for (FareRule fareRule : fareRules) {
            if (fareRule.getRouteId().equals(trip.getRouteId())) {
                applicableFareId = fareRule.getFareId();
                break;
            }
        }

        if (applicableFareId != null) {
            for (Fare fareAttribute : fareAttributes) {
                if (fareAttribute.getFareId().equals(applicableFareId)) {
                    return fareAttribute.getPrice();
                }
            }
        }

        return -1;
    }
}

