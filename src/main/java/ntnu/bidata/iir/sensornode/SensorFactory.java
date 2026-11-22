package ntnu.bidata.iir.sensornode;

/** Creates sensors from a spec like "temp:1" (type:instance). */
public final class SensorFactory {
    private SensorFactory() { }

    public static Sensor create(String spec) {
        String type = spec.split(":", 2)[0];
        return switch (type) {
            case "temp" -> new RandomWalkSensor(spec, 21.0, -10.0, 40.0, 0.4);
            case "hum" -> new RandomWalkSensor(spec, 45.0, 10.0, 90.0, 1.5);
            case "light" -> new RandomWalkSensor(spec, 300.0, 0.0, 1000.0, 25.0);
            default -> throw new IllegalArgumentException("Unknown sensor type: " + type);
        };
    }
}
