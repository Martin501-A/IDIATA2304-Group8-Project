package ntnu.bidata.iir.controlpanel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the control panel currently knows about one sensor node. */
final class NodeState {
    private final String id;
    private final Map<String, SensorSeries> sensors = new LinkedHashMap<>();
    private final Map<String, String> actuatorStates = new LinkedHashMap<>();

    NodeState(String id, String[] sensorIds, String[] actuatorIds) {
        this.id = id;
        for (String sensorId : sensorIds) {
            sensors.put(sensorId, new SensorSeries(sensorId));
        }
        for (String actuatorId : actuatorIds) {
            actuatorStates.put(actuatorId, "OFF"); // nodes start with everything off
        }
    }

    String id() {
        return id;
    }

    List<SensorSeries> sensors() {
        return new ArrayList<>(sensors.values());
    }

    Map<String, String> actuatorStates() {
        return actuatorStates;
    }

    /** @return a description if this reading is an anomaly, otherwise null */
    String applyReading(String sensorId, double value) {
        SensorSeries series = sensors.computeIfAbsent(sensorId, SensorSeries::new);
        String anomaly = AnomalyDetector.check(sensorId, value, series.latest());
        series.add(value, anomaly);
        return anomaly;
    }

    void applyStatus(String actuatorId, String state) {
        actuatorStates.put(actuatorId, state);
    }

    boolean hasAnomaly() {
        return sensors.values().stream().anyMatch(s -> s.anomaly() != null);
    }
}
