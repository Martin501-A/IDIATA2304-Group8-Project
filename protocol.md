# SHA Smart Home Protocol (DRAFT - review as a group)

## 1. Introduction
This document describes the SHA protocol, an application-layer protocol on top of TCP that lets
simulated sensor/actuator nodes report data and be controlled from control panels through a
central broker.

## 2. Terminology
| Term | Meaning |
|---|---|
| Broker | Central server. Accepts all connections, routes data and commands, enforces access levels. |
| Sensor node | Simulated device with several sensors and actuators. Identified by a unique node id (e.g. `node1`). |
| Control panel | User interface client (CLI or Swing GUI) used by a person with a role. |
| Sensor id / actuator id | `type:instance`, e.g. `temp:2`, `fan:1`. The type is the part. |
| Role | Access level of a panel: `ADMIN`, `OWNER`, `TECHNICIAN`, `GUEST`. |
| Subscription | A panel's request to receive READING/STATUS messages of one node. |

## 3. Transport protocol
TCP only. *Why:* commands such as "unlock door" must not be lost or reordered, and TCP also tells
us when a peer disappears. The cost is some latency for frequent readings which is acceptable
for readings every 2 seconds.

## 4. Port
TCP port **5050** on the broker.

## 5. Architecture
- Actors: broker, sensor nodes, control panels.
- The broker is the only server. Sensor nodes and panels are clients that open one TCP
  connection each to the broker; they never talk to each other directly.
- *Why a broker:* nodes and panels only need the broker's address (minimal configuration), and
  there is one place to route messages and check permissions.

## 6. Flow of information
1. Node connects and sends `REGISTER`; broker answers `OK` and pushes `NODE_INFO` to all panels.
2. Panel connects and sends `LOGIN`; broker answers `OK`, then sends `NODE_INFO` for every node already connected.
3. Panel sends `SUBSCRIBE|nodeId` for each node it wants data from (the GUI does this automatically on every `NODE_INFO`).
4. Node **pushes** a `READING` per sensor every 2 s; the broker forwards it to subscribers with the node id added.
5. Panel sends `COMMAND`; the broker checks node, actuator and role, forwards it to the node, node applies it and pushes `STATUS`, which is forwarded to subscribers.
6. When a node's connection closes the broker sends `NODE_GONE` to all panels.
Panels can also **pull** the node list with `LIST_NODES`.
Mix of push (readings, status, node announcements) and publish-subscribe (readings/status per node), plus request/response (commands, list).

## 7. Type of protocol
Connection-oriented (TCP) and **stateful**: the broker remembers per connection whether it is a node or a panel, its id, its role, its capabilities and its subscriptions. Messages other than `REGISTER`/`LOGIN` are rejected (401) before that.

## 8. Types and constants
- Separator between fields: `|`. Separator inside list fields: `,`.
- Actuator actions: `ON`, `OFF` (initial state of every actuator: `OFF`).
- Sensor types: `temp`, `hum`, `light`. Actuator types: `fan`, `heater`, `window`, `light`, `door`. New types are added in `SensorFactory` / `ActuatorFactory` only.
- Error codes: `400` bad request, `401` not logged in or wrong password, `403` forbidden for this role, `404` unknown node or actuator, `409` id already in use / already registered.
- Roles and rights: ADMIN and OWNER control every actuator; TECHNICIAN every actuator except `door` and `lock`; GUEST only `fan` and `light`. Everyone may view all data.

## 9. Message format
Plain text, UTF-8

| Message | Sent by -> to | Format |
|---|---|---|
| REGISTER | node -> broker | `REGISTER\|nodeId\|sensors\|actuators` (lists comma separated) |
| LOGIN | panel -> broker | `LOGIN\|panelId\|role\|password` |
| SUBSCRIBE | panel -> broker | `SUBSCRIBE\|nodeId` |
| LIST_NODES | panel -> broker | `LIST_NODES` |
| COMMAND | panel -> broker | `COMMAND\|nodeId\|actuatorId\|action` |
| COMMAND | broker -> node | `COMMAND\|actuatorId\|action` |
| READING | node -> broker | `READING\|sensorId\|value` (decimal, dot, e.g. `21.5`) |
| READING | broker -> panel | `READING\|nodeId\|sensorId\|value` |
| STATUS | node -> broker | `STATUS\|actuatorId\|state` |
| STATUS | broker -> panel | `STATUS\|nodeId\|actuatorId\|state` |
| NODE_INFO | broker -> panel | `NODE_INFO\|nodeId\|sensors\|actuators` |
| NODE_GONE | broker -> panel | `NODE_GONE\|nodeId` |
| NODE_LIST | broker -> panel | `NODE_LIST\|id1,id2,...` |
| OK | broker -> client | `OK` |
| ERROR | broker -> client | `ERROR\|code\|text` |

## 10. Errors and how nodes react
- **Malformed line / unknown type / missing field (broker):** reply `ERROR|400|...`, keep the connection open.
- **Message not allowed for this client type (e.g. a node sends SUBSCRIBE):** `ERROR|400`.
- **Not registered/logged in:** `ERROR|401`. **Wrong password:** `ERROR|401`, connection stays open for a retry.
- **Role not allowed to control the actuator:** `ERROR|403`, nothing is forwarded.
- **Unknown node or actuator:** `ERROR|404`.
- **Duplicate node/panel id:** `ERROR|409`.
- **Node receives an invalid command** (unknown actuator, action other than ON/OFF): ignored and logged, no reply. *Known limitation, candidate improvement.*
- **Panel/node receives a malformed line:** logged and ignored.
- **Connection lost:** the broker removes the client and tells panels (`NODE_GONE`). Panels/nodes currently report the loss and stop (no reconnect yet).

## 11. Realistic scenario
A guest at home wants more air; the owner later checks the room.
1. `node1` (temp:1, hum:1 / fan:1, heater:1, door:1) connects: `REGISTER|node1|temp:1,hum:1|fan:1,heater:1,door:1` -> `OK`.
2. Guest panel logs in: `LOGIN|tablet|GUEST|guest123` -> `OK`, then `NODE_INFO|node1|...`, panel replies `SUBSCRIBE|node1` -> `OK`.
3. Every 2 s: node `READING|temp:1|21.4`, broker to panel `READING|node1|temp:1|21.4`; the GUI draws the chart.
4. Guest presses fan ON: `COMMAND|node1|fan:1|ON` -> broker `COMMAND|fan:1|ON` to node -> node `STATUS|fan:1|ON` -> panels `STATUS|node1|fan:1|ON`. The state label turns green.
5. Guest tries the door: the button is disabled in the GUI; a raw `COMMAND|node1|door:1|ON` gets `ERROR|403|Role GUEST may not control door:1`.
6. The heater is left on, temperature passes 30 degrees: the panel's anomaly detector flags `temp:1` red and logs it.

## 12. Reliability
Provided by TCP (ordering, retransmission). No application-level acknowledgement of commands beyond `OK` from the broker and the node's `STATUS`. *Planned extra:* reconnect with back-off and buffering of readings on nodes.

## 13. Security
Role-based access control enforced by the broker (see section 8). Passwords are fixed per role and sent in plain text; the connection is not encrypted. This is a deliberate simplification for the first version. *Planned extra:* TLS (`SSLSocket`) and per-user accounts.

## 14. Assumptions
- Node ids are chosen by whoever starts the node and must be unique while connected.
- Every actuator is binary (`ON`/`OFF`); `door:ON` is read as "unlocked/open".
- Nodes start with all actuators `OFF`; panels assume this until a `STATUS` arrives.
- A heater raises, a fan lowers the simulated temperature of its node.
- All roles may view all nodes; "selected sensors for guests" is not implemented yet.
- Anomaly detection runs in the control panel (range and jump rules per sensor type).
