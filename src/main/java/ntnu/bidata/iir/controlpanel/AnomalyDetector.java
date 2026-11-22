package ntnu.bidata.iir.controlpanel;

import java.util.Locale;
import java.util.Map;

/**
 * Flags suspicious sensor readings: outside the normal range, or a sudden jump
 * compared with the previous reading.
 */
final class AnomalyDetector {
    /** Normal range and largest believable change between two consecutive readings. */
    private record Rule(String name, double min, double max, double maxJump) { }

    private static final Map<String, Rule> RULES = Map.of(
            "temp", new Rule("Temperature", 5.0, 30.0, 3.0),
            "hum", new Rule("Humidity", 20.0, 80.0, 10.0),
            "light", new Rule("Light", 0.0, 1000.0, 300.0));

    private AnomalyDetector() { }

    /**
     * @param previous the previous reading of the same sensor, or null if none
     * @return a description of the problem, or null if the reading looks normal
     */
    static String check(String sensorId, double value, Double previous) {
        Rule rule = RULES.get(sensorId.split(":", 2)[0]);
        if (rule == null) {
            return null;
        }
        if (value < rule.min()) {
            return String.format(Locale.ROOT, "%s too low (%.1f < %.1f)", rule.name(), value, rule.min());
        }
        if (value > rule.max()) {
            return String.format(Locale.ROOT, "%s too high (%.1f > %.1f)", rule.name(), value, rule.max());
        }
        if (previous != null && Math.abs(value - previous) > rule.maxJump()) {
            return String.format(Locale.ROOT, "%s jumped from %.1f to %.1f", rule.name(), previous, value);
        }
        return null;
    }
}
