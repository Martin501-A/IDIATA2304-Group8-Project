package ntnu.bidata.iir.controlpanel;

import java.io.IOException;
import java.util.Scanner;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;

/**
 * Command-line control panel.
 * Usage: ControlPanelMain [host] [panelId] [role] [password]
 * Commands: list | sub NODE | cmd NODE ACTUATOR ON|OFF | quit
 */
public final class ControlPanelMain {
    private ControlPanelMain() { }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        String panelId = args.length > 1 ? args[1] : "panel1";
        String role = args.length > 2 ? args[2] : "OWNER";
        String password = args.length > 3 ? args[3] : "owner123";

        try (Connection connection = Connection.connect(host, Protocol.PORT)) {
            connection.send(Message.of(MessageType.LOGIN, panelId, role, password));

            Thread reader = new Thread(() -> printIncoming(connection), "panel-reader");
            reader.setDaemon(true);
            reader.start();

            runMenu(connection);
        }
    }

    private static void runMenu(Connection connection) {
        System.out.println("Commands: list | sub NODE | cmd NODE ACTUATOR ON|OFF | quit");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String[] p = scanner.nextLine().trim().split("\\s+");
            switch (p[0]) {
                case "list" -> connection.send(Message.of(MessageType.LIST_NODES));
                case "sub" -> {
                    if (p.length == 2) {
                        connection.send(Message.of(MessageType.SUBSCRIBE, p[1]));
                    } else {
                        System.out.println("Usage: sub NODE");
                    }
                }
                case "cmd" -> {
                    if (p.length == 4) {
                        connection.send(Message.of(MessageType.COMMAND, p[1], p[2], p[3].toUpperCase()));
                    } else {
                        System.out.println("Usage: cmd NODE ACTUATOR ON|OFF");
                    }
                }
                case "quit" -> {
                    return;
                }
                default -> System.out.println("Unknown command");
            }
        }
    }

    /** Prints everything the broker sends. */
    private static void printIncoming(Connection connection) {
        try {
            while (true) {
                Message m;
                try {
                    m = connection.receive();
                } catch (ProtocolException e) {
                    System.out.println("Malformed message from broker: " + e.getMessage());
                    continue;
                }
                if (m == null) {
                    System.out.println("Broker closed the connection.");
                    return;
                }
                System.out.println("< " + m.serialize());
            }
        } catch (IOException e) {
            System.out.println("Connection lost: " + e.getMessage());
        }
    }
}
