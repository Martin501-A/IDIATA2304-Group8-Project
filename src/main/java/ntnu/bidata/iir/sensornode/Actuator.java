package ntnu.bidata.iir.sensornode;

/** A simulated actuator (fan, heater, window...). State is a simple ON/OFF string for now. */
public class Actuator {
    private final String id;
    private volatile String state = "OFF";

    public Actuator(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String state() {
        return state;
    }

    /** @return true if the action was applied. */
    public boolean apply(String action) {
        if (action.equals("ON") || action.equals("OFF")) {
            state = action;
            return true;
        }
        return false;
    }
}
