package ntnu.bidata.iir.sensornode;

import java.util.LinkedHashMap;
import java.util.Map;

/** The simulated device: owns its sensors and actuators, knows nothing about networking. */
public class SensorNode {
    private final String id;
    private final Map<String, Sensor> sensors = new LinkedHashMap<>();
    private final Map<String, Actuator> actuators = new LinkedHashMap<>();

    public SensorNode(String id, String[] sensorSpecs, String[] actuatorSpecs) {
        this.id = id;
        for (String spec : sensorSpecs) {
            sensors.put(spec, SensorFactory.create(spec));
        }
        for (String spec : actuatorSpecs) {
            actuators.put(spec, ActuatorFactory.create(spec));
        }
    }

    public String id() {
        return id;
    }

    public Map<String, Sensor> sensors() {
        return sensors;
    }

    public Map<String, Actuator> actuators() {
        return actuators;
    }

    /**
     * Applies an action to an actuator and lets it influence the simulated environment.
     *
     * @return true if the actuator exists and understood the action
     */
    public boolean command(String actuatorId, String action) {
        Actuator actuator = actuators.get(actuatorId);
        if (actuator == null || !actuator.apply(action)) {
            return false;
        }
        updateTemperatureDrift();
        return true;
    }

    /** A running heater warms the room, a running fan cools it. */
    private void updateTemperatureDrift() {
        double drift = 0;
        for (Actuator actuator : actuators.values()) {
            if (actuator.state().equals("ON")) {
                String type = actuator.id().split(":", 2)[0];
                drift += switch (type) {
                    case "heater" -> 0.3;
                    case "fan" -> -0.2;
                    default -> 0.0;
                };
            }
        }
        for (Sensor sensor : sensors.values()) {
            if (sensor.id().startsWith("temp") && sensor instanceof RandomWalkSensor walker) {
                walker.setDrift(drift);
            }
        }
    }
}
