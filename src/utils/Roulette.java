package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Roulette {
	
	private static double total(List<Double> probabilities) {
		double res = 0;
		for (Double e : probabilities) {
			res += e;
		}
		return res;
	}
	
	public static int choice(List<Double> probabilities, double random) {
		int res = 0;
		double sum = 0.0;
		double total = random * total(probabilities);
		for (int i = 0; i < probabilities.size(); i++) {
			sum += probabilities.get(i);
			if (total <= sum) {
				return (i<probabilities.size()?i:i-1);
			}
		}
		return res;
	}
	public static int choice(Double[] probabilities, double random) {
		return choice(Arrays.asList(probabilities), random);
	}
	
	public static int choice(double[] probabilities, double random) {
		List<Double> list = new ArrayList<>();
		for (double e : probabilities) {
			list.add(e);
		}
		return choice(list, random);
	}
}
