package ntnu.bidata.iir.sensornode;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;

/**
 * simulated sensor nodes.
 * Usage: SensorNodeMain [host] [nodeId] [sensors] [actuators]
 * Example: SensorNodeMain localhost node1 temp:1,hum:1 fan:1,heater:1
 */
public final class SensorNodeMain {
    private static final int READING_INTERVAL_SECONDS = 2;

    private SensorNodeMain() { }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        String nodeId = args.length > 1 ? args[1] : "node1";
        String sensors = args.length > 2 ? args[2] : "temp:1,hum:1";
        String actuators = args.length > 3 ? args[3] : "fan:1,heater:1";

        SensorNode node = new SensorNode(nodeId,
                sensors.split(Protocol.LIST_SEPARATOR), actuators.split(Protocol.LIST_SEPARATOR));

        try (Connection connection = Connection.connect(host, Protocol.PORT)) {
            connection.send(Message.of(MessageType.REGISTER, nodeId, sensors, actuators));

            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> sendReadings(node, connection),
                    1, READING_INTERVAL_SECONDS, TimeUnit.SECONDS);

            listen(node, connection);
            scheduler.shutdownNow();
        }
        System.out.println("Disconnected from broker.");
    }

    private static void sendReadings(SensorNode node, Connection connection) {
        node.sensors().values().forEach(sensor -> connection.send(Message.of(
                MessageType.READING, sensor.id(), String.format(Locale.ROOT, "%.1f", sensor.read()))));
    }

    /** Handles messages from the broker until the connection closes. */
    private static void listen(SensorNode node, Connection connection) throws IOException {
        while (true) {
            Message m;
            try {
                m = connection.receive();
            } catch (ProtocolException e) {
                System.out.println("Ignoring malformed message: " + e.getMessage());
                continue;
            }
            if (m == null) {
                return;
            }
            switch (m.type()) {
                case COMMAND -> onCommand(node, connection, m);
                case OK -> { }
                case ERROR -> System.out.println("Broker error: " + m.args());
                default -> System.out.println("Ignoring unexpected message: " + m.type());
            }
        }
    }

    private static void onCommand(SensorNode node, Connection connection, Message m) {
        try {
            String actuatorId = m.arg(0);
            if (!node.command(actuatorId, m.arg(1))) {
                System.out.println("Rejected command: " + m.args());
                return;
            }
            String state = node.actuators().get(actuatorId).state();
            System.out.println("Actuator " + actuatorId + " -> " + state);
            connection.send(Message.of(MessageType.STATUS, actuatorId, state));
        } catch (ProtocolException e) {
            System.out.println("Rejected command: " + e.getMessage());
        }
    }
}
