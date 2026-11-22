package ntnu.bidata.iir.common;

/** All message types in the protocol. */
public enum MessageType {
    // sensor node -> broker
    REGISTER,   // REGISTER|nodeId|sensors|actuators
    READING,    // node->broker: READING|sensorId|value   broker->panel: READING|nodeId|sensorId|value
    STATUS,     // node->broker: STATUS|actuatorId|state  broker->panel: STATUS|nodeId|actuatorId|state

    // control panel -> broker
    LOGIN,      // LOGIN|panelId|role|password
    SUBSCRIBE,  // SUBSCRIBE|nodeId
    LIST_NODES, // LIST_NODES
    COMMAND,    // panel->broker: COMMAND|nodeId|actuatorId|action   broker->node: COMMAND|actuatorId|action

    // broker -> anyone
    NODE_LIST,  // NODE_LIST|id1,id2,...
    NODE_INFO,  // NODE_INFO|nodeId|sensors|actuators   (pushed when a node joins, and on login)
    NODE_GONE,  // NODE_GONE|nodeId                     (pushed when a node disconnects)
    OK,         // OK
    ERROR       // ERROR|code|text
}
