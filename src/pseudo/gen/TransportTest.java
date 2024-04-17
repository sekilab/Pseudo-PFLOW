package pseudo.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;

public class TransportTest {

    enum ETransport { MIX, CAR, WALK, NOT_DEFINED }

    public static void main(String[] args) {
        // Create an instance of Person
        Person person = new Person(true);  // Person has a car
        double mixedCost = 1230.0;
        double roadCost = 1220.0;
        double walkCost = 520.0;

        // Test the functionality
        ETransport nextMode = getOptimalTransport(person, mixedCost, roadCost, walkCost);
        System.out.println("Optimal Transport Mode: " + nextMode);  // Expected: CAR
    }

    public static ETransport getOptimalTransport(Person person, double mixedCost, double roadCost, double walkCost) {
        Map<ETransport, Double> choices = new LinkedHashMap<>();
        choices.put(ETransport.MIX, mixedCost);
        choices.put(ETransport.WALK, walkCost);

        if(person.hasCar()){
            choices.put(ETransport.CAR, roadCost);
        }

        return choices.entrySet()
                .stream()
                .min(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(ETransport.NOT_DEFINED);
    }

    static class Person {
        private boolean hasCar;

        public Person(boolean hasCar) {
            this.hasCar = hasCar;
        }

        public boolean hasCar() {
            return hasCar;
        }
    }
}
