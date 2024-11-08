package gtfs;

import org.onebusaway.gtfs.model.FareAttribute;

import java.util.List;

public class FareCalculator {

    // 计算票价
    public static double calculateFare(Trip trip, List<FareRule> fareRules, List<Fare> fareAttributes, Stop originStop, Stop destinationStop) {
        String applicableFareId = null;

        // Step 1: 在 fare_rules.txt 中查找适用的 fare_id
        for (FareRule fareRule : fareRules) {
            if (fareRule.getRouteId().equals(trip.getRouteId())) {
                applicableFareId = fareRule.getFareId();
                break;
            }
        }

        // Step 2: 如果找到适用的 fare_id，查找 fare_attributes.txt 中的票价
        if (applicableFareId != null) {
            for (Fare fareAttribute : fareAttributes) {
                if (fareAttribute.getFareId().equals(applicableFareId)) {
                    return fareAttribute.getPrice();  // 返回票价
                }
            }
        }

        return -1;  // 如果没有找到适用的票价规则，返回 -1 表示无效
    }
}

