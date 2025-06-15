package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Softmax {
    public static List<Double> softmax(List<Double> input) {
        return softmax(input, 1.0);
    }

    /**
     * Applies the softmax function with a temperature parameter to the input arrays.
     * @param input The input arrays.
     * @param temperature The temperature value (> 0). Lower is sharper (more confident predictions), while higher is smoother (less confident).
     * @return The softmax probabilities.
     */
    public static List<Double> softmax(List<Double> input, double temperature) {
        if  (temperature < 0) {
            throw new IllegalArgumentException("Temperature must be greater than 0.");
        }
        List<Double> scaledInput = new ArrayList<>();
        for (Double a : input) {
            scaledInput.add(a / temperature);
        }

        double maxValue = !scaledInput.isEmpty() ? Collections.max(scaledInput) : 0.0;
        double sumExp = 0.0;
        List<Double> softmax = new ArrayList<>();

        for (Double a : scaledInput) {
            softmax.add(Math.exp(a - maxValue));
            sumExp += Math.exp(a - maxValue);
        }

        for (int i = 0; i < softmax.size(); i++) {
            softmax.set(i, softmax.get(i) / sumExp);
        }

        return softmax;
    }
}
