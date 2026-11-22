package ntnu.bidata.iir;

import ntnu.bidata.iir.broker.BrokerMain;
import ntnu.bidata.iir.controlpanel.ControlPanelGuiMain;
import ntnu.bidata.iir.sensornode.SensorNodeMain;

/**
 * Starts the whole smart home system in one program.
 *
 * Each part talks through TCP sockets (localhost:5050).
 *
 * Closing the GUI window ends the program.
 *
 * Demo logins: OWNER owner123, ADMIN admin123, TECHNICIAN tech123, GUEST guest123.
 */
public final class SmartHomeLauncher {
    private static final long STARTUP_DELAY_MS = 500;

    private SmartHomeLauncher() { }

    public static void main(String[] args) throws Exception {
        startInBackground("broker", () -> BrokerMain.main(new String[0]));
        Thread.sleep(STARTUP_DELAY_MS); // give the broker time to start listening

        startInBackground("node1", () -> SensorNodeMain.main(
                new String[] {"localhost", "node1", "temp:1,hum:1", "fan:1,heater:1"}));
        startInBackground("node2", () -> SensorNodeMain.main(
                new String[] {"localhost", "node2", "temp:1,light:1", "window:1,fan:1,door:1"}));
        Thread.sleep(STARTUP_DELAY_MS); // let the nodes register before the panel lists them

        ControlPanelGuiMain.main(new String[0]);
    }

    /** Runs a blocking task on a daemon thread so it stops when the panel quits. */
    private static void startInBackground(String name, ThrowingRunnable task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println(name + " stopped: " + e.getMessage());
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
