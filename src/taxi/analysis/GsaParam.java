package taxi.analysis;

/**
 * One row of {@code gsa_params.csv}: a parameter to be perturbed in
 * Morris elementary effects screening or Sobol indices computation.
 */
final class GsaParam {
    final String name;       // human-readable label (e.g. "APP_PREFERRED share")
    final double defaultVal; // default value (current production setting)
    final double min;        // sensitivity-range lower bound
    final double max;        // sensitivity-range upper bound
    final String configKey;  // key in taxi_config.properties (e.g. "taxi.type.app_preferred.share")

    GsaParam(String name, double defaultVal, double min, double max, String configKey) {
        this.name = name;
        this.defaultVal = defaultVal;
        this.min = min;
        this.max = max;
        this.configKey = configKey;
    }

    /** Map a unit-interval value u∈[0,1] to a parameter value via linear interpolation. */
    double scale(double u) {
        return min + u * (max - min);
    }

    @Override
    public String toString() {
        return String.format("%s [%s = %g, range=[%g, %g]]", name, configKey, defaultVal, min, max);
    }
}
