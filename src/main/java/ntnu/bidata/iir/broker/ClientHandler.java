package ntnu.bidata.iir.broker;

import java.io.IOException;
import java.util.List;
import ntnu.bidata.iir.common.Connection;
import ntnu.bidata.iir.common.Message;
import ntnu.bidata.iir.common.MessageType;
import ntnu.bidata.iir.common.Protocol;
import ntnu.bidata.iir.common.ProtocolException;
import ntnu.bidata.iir.common.Role;

/** Handles one TCP client (sensor node or control panel) for its whole lifetime. */

final class ClientHandler implements Runnable {
    private enum Kind { UNKNOWN, SENSOR_NODE, PANEL }

    private final Connection connection;
    private final Registry registry;
    private volatile Kind kind = Kind.UNKNOWN;
    private volatile String id;
    private volatile Role role;            // panels only
    private volatile String sensors = "";   // nodes only, e.g. "temp:1,hum:1"
    private volatile String actuators = ""; // nodes only, e.g. "fan:1,heater:1"

    ClientHandler(Connection connection, Registry registry) {
        this.connection = connection;
        this.registry = registry;
    }

    void send(Message message) {
        connection.send(message);
    }

    String id() {
        return id;
    }

    /** The NODE_INFO message that describes this node. only used for sensor nodes. */
    Message nodeInfo() {
        return Message.of(MessageType.NODE_INFO, id, sensors, actuators);
    }

    @Override
    public void run() {
        System.out.println("Client connected: " + connection.remoteAddress());
        try {
            Message message;
            while ((message = nextMessage()) != null) {
                handle(message);
            }
        } catch (IOException e) {
            System.out.println("Connection error (" + id + "): " + e.getMessage());
        } finally {
            registry.remove(this);
            if (kind == Kind.SENSOR_NODE) {
                registry.broadcastToPanels(Message.of(MessageType.NODE_GONE, id));
            }
            connection.close();
            System.out.println("Client disconnected: " + (id != null ? id : connection.remoteAddress()));
        }
    }

    /** Reads the next valid message. Malformed lines get an ERROR reply and are skipped. */
    private Message nextMessage() throws IOException {
        while (true) {
            try {
                return connection.receive();
            } catch (ProtocolException e) {
                error(Protocol.ERR_BAD_REQUEST, e.getMessage());
            }
        }
    }

    private void handle(Message m) {
        try {
            switch (m.type()) {
                case REGISTER -> onRegister(m);
                case LOGIN -> onLogin(m);
                case READING, STATUS -> onNodeData(m);
                case SUBSCRIBE -> onSubscribe(m);
                case LIST_NODES -> onListNodes();
                case COMMAND -> onCommand(m);
                default -> error(Protocol.ERR_BAD_REQUEST, "Clients may not send " + m.type());
            }
        } catch (ProtocolException e) {
            error(Protocol.ERR_BAD_REQUEST, e.getMessage());
        }
    }

    private void onRegister(Message m) {
        if (kind != Kind.UNKNOWN) {
            error(Protocol.ERR_CONFLICT, "Already registered");
            return;
        }
        String nodeId = m.arg(0);
        sensors = m.arg(1);
        actuators = m.arg(2);
        if (!registry.registerNode(nodeId, this)) {
            error(Protocol.ERR_CONFLICT, "Node id already in use: " + nodeId);
            return;
        }
        kind = Kind.SENSOR_NODE;
        id = nodeId;
        send(Message.of(MessageType.OK));
        registry.broadcastToPanels(nodeInfo());
        System.out.println("Node registered: " + nodeId);
    }

    private void onLogin(Message m) {
        if (kind != Kind.UNKNOWN) {
            error(Protocol.ERR_CONFLICT, "Already logged in");
            return;
        }
        String panelId = m.arg(0);
        Role requestedRole = Role.parse(m.arg(1));
        if (!Authenticator.isValid(requestedRole, m.arg(2))) {
            error(Protocol.ERR_UNAUTHENTICATED, "Wrong password for role " + requestedRole);
            return;
        }
        if (!registry.registerPanel(panelId, this)) {
            error(Protocol.ERR_CONFLICT, "Panel id already in use: " + panelId);
            return;
        }
        kind = Kind.PANEL;
        id = panelId;
        role = requestedRole;
        send(Message.of(MessageType.OK));
        // Tell the new panel about every node that is already connected.
        registry.allNodes().forEach(node -> send(node.nodeInfo()));
        System.out.println("Panel logged in: " + panelId + " as " + requestedRole);
    }

    /** READING and STATUS from a node are forwarded to subscribers, tagged with the node id. */
    private void onNodeData(Message m) {
        if (!require(Kind.SENSOR_NODE)) {
            return;
        }
        Message forwarded = Message.of(m.type(), id, m.arg(0), m.arg(1));
        registry.publish(id, forwarded);
    }

    private void onSubscribe(Message m) {
        if (!require(Kind.PANEL)) {
            return;
        }
        String nodeId = m.arg(0);
        if (registry.node(nodeId) == null) {
            error(Protocol.ERR_NOT_FOUND, "No such node: " + nodeId);
            return;
        }
        registry.subscribe(nodeId, this);
        send(Message.of(MessageType.OK));
    }

    private void onListNodes() {
        if (!require(Kind.PANEL)) {
            return;
        }
        String joined = String.join(Protocol.LIST_SEPARATOR, registry.nodeIds());
        send(Message.of(MessageType.NODE_LIST, joined));
    }

    private void onCommand(Message m) {
        if (!require(Kind.PANEL)) {
            return;
        }
        String nodeId = m.arg(0);
        ClientHandler target = registry.node(nodeId);
        if (target == null) {
            error(Protocol.ERR_NOT_FOUND, "No such node: " + nodeId);
            return;
        }
        String actuatorId = m.arg(1);
        if (!List.of(target.actuators.split(Protocol.LIST_SEPARATOR)).contains(actuatorId)) {
            error(Protocol.ERR_NOT_FOUND, "Node " + nodeId + " has no actuator " + actuatorId);
            return;
        }
        if (!role.mayControl(actuatorId)) {
            error(Protocol.ERR_FORBIDDEN, "Role " + role + " may not control " + actuatorId);
            return;
        }
        target.send(Message.of(MessageType.COMMAND, actuatorId, m.arg(2)));
        send(Message.of(MessageType.OK));
    }

    private boolean require(Kind required) {
        if (kind == required) {
            return true;
        }
        if (kind == Kind.UNKNOWN) {
            error(Protocol.ERR_UNAUTHENTICATED, "Register or log in first");
        } else {
            error(Protocol.ERR_BAD_REQUEST, "Not allowed for this client type");
        }
        return false;
    }

    private void error(String code, String text) {
        send(Message.of(MessageType.ERROR, code, text.replace(Protocol.SEPARATOR, "/")));
    }
}
