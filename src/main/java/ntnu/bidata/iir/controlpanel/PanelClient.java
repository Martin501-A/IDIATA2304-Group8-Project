package ntnu.bidata.iir.controlpanel;

import java.io.Closeable;
import java.io.IOException;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;
import ntnu.bidata.iir.common.Role;

/**
 * Network side of a control panel: logs in, subscribes to every node the broker announces,
 * and turns incoming messages into callbacks. Knows nothing about Swing.
 * Callbacks arrive on the reader thread, so a GUI must hand them over to its own thread.
 */
final class PanelClient implements Closeable {

    /** Receives everything the broker tells this panel. */
    interface Listener {
        void onNodeInfo(String nodeId, String[] sensors, String[] actuators);

        void onNodeGone(String nodeId);

        void onReading(String nodeId, String sensorId, String value);

        void onStatus(String nodeId, String actuatorId, String state);

        void onError(String code, String text);

        void onDisconnected(String reason);
    }

    private final Connection connection;
    private final Listener listener;

    private PanelClient(Connection connection, Listener listener) {
        this.connection = connection;
        this.listener = listener;
    }

    /**
     * Connects and logs in. Blocks until the broker accepts or rejects the login.
     *
     * @throws IOException if the broker is unreachable or rejects the login
     */
    static PanelClient connect(String host, String panelId, Role role, String password, Listener listener)
            throws IOException {
        Connection connection = Connection.connect(host, Protocol.PORT);
        PanelClient client = new PanelClient(connection, listener);
        try {
            connection.send(Message.of(MessageType.LOGIN, panelId, role.name(), password));
            client.awaitLoginResult();
        } catch (IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
        Thread reader = new Thread(client::readLoop, "panel-reader");
        reader.setDaemon(true);
        reader.start();
        return client;
    }

    private void awaitLoginResult() throws IOException {
        while (true) {
            Message m = connection.receive();
            if (m == null) {
                throw new IOException("Broker closed the connection");
            }
            if (m.type() == MessageType.OK) {
                return;
            }
            if (m.type() == MessageType.ERROR) {
                throw new IOException("Login failed: " + m.arg(1));
            }
            dispatch(m);
        }
    }

    private void readLoop() {
        try {
            while (true) {
                Message m;
                try {
                    m = connection.receive();
                } catch (ProtocolException e) {
                    listener.onError(Protocol.ERR_BAD_REQUEST, "Malformed message from broker: " + e.getMessage());
                    continue;
                }
                if (m == null) {
                    listener.onDisconnected("The broker closed the connection");
                    return;
                }
                dispatch(m);
            }
        } catch (IOException e) {
            listener.onDisconnected("Connection lost: " + e.getMessage());
        }
    }

    private void dispatch(Message m) {
        try {
            switch (m.type()) {
                case NODE_INFO -> {
                    listener.onNodeInfo(m.arg(0), splitList(m.arg(1)), splitList(m.arg(2)));
                    connection.send(Message.of(MessageType.SUBSCRIBE, m.arg(0)));
                }
                case NODE_GONE -> listener.onNodeGone(m.arg(0));
                case READING -> listener.onReading(m.arg(0), m.arg(1), m.arg(2));
                case STATUS -> listener.onStatus(m.arg(0), m.arg(1), m.arg(2));
                case ERROR -> listener.onError(m.arg(0), m.arg(1));
                default -> { } // OK and NODE_LIST need no action
            }
        } catch (ProtocolException e) {
            listener.onError(Protocol.ERR_BAD_REQUEST, "Malformed message from broker: " + e.getMessage());
        }
    }

    private static String[] splitList(String text) {
        return text.isBlank() ? new String[0] : text.split(Protocol.LIST_SEPARATOR);
    }

    void sendCommand(String nodeId, String actuatorId, String action) {
        connection.send(Message.of(MessageType.COMMAND, nodeId, actuatorId, action));
    }

    @Override
    public void close() {
        connection.close();
    }
}
