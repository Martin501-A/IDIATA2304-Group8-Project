package ntnu.bidata.iir.sensornode;

import java.util.Set;

/** Creates actuators from a spec like "fan:1". */
public final class ActuatorFactory {
    private static final Set<String> KNOWN_TYPES = Set.of("fan", "heater", "window", "light", "door");

    private ActuatorFactory() { }

    public static Actuator create(String spec) {
        String type = spec.split(":", 2)[0];
        if (!KNOWN_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown actuator type: " + type);
        }
        return new Actuator(spec);
    }
}
