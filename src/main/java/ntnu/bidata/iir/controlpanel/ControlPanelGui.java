package ntnu.bidata.iir.controlpanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import ntnu.bidata.iir.common.Role;

/**
 * Control panel: node list, sensors, history chart and actuator controls
 * for the selected node and an event.
 *
 * All model and component changes happen on the Swing thread. Callbacks from the network
 * thread are therefore wrapped in SwingUtilities.invokeLater.
 */
final class ControlPanelGui extends JFrame implements PanelClient.Listener {
    private static final Color ALERT = new Color(0xD64545);
    private static final Color ON_COLOR = new Color(0x2E9E5B);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Role role;
    private final Map<String, NodeState> nodes = new LinkedHashMap<>();
    private final DefaultListModel<String> nodeListModel = new DefaultListModel<>();
    private final JList<String> nodeList = new JList<>(nodeListModel);
    private final SensorTableModel tableModel = new SensorTableModel();
    private final JTable sensorTable = new JTable(tableModel);
    private final LineChartPanel chart = new LineChartPanel();
    private final JLabel nodeTitle = new JLabel("Select a node on the left");
    private final JPanel actuatorPanel = new JPanel();
    private final Map<String, JLabel> actuatorStateLabels = new HashMap<>();
    private final JTextArea log = new JTextArea(7, 40);

    private PanelClient client;
    private NodeState selected;

    ControlPanelGui(String panelId, Role role) {
        super("SHA Control Panel - " + panelId + " (" + role + ")");
        this.role = role;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildLayout();
        setSize(960, 680);
        setLocationRelativeTo(null);
    }

    /** Called once the login succeeded; enables sending commands. */
    void attach(PanelClient client) {
        this.client = client;
        log("Logged in as " + role);
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeList.setCellRenderer(new NodeCellRenderer());
        nodeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showNode(nodes.get(nodeList.getSelectedValue()));
            }
        });
        JScrollPane nodeScroll = new JScrollPane(nodeList);
        nodeScroll.setBorder(BorderFactory.createTitledBorder("Sensor nodes"));
        nodeScroll.setPreferredSize(new Dimension(190, 100));

        nodeTitle.setFont(nodeTitle.getFont().deriveFont(Font.BOLD, 16f));
        nodeTitle.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        sensorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sensorTable.setDefaultRenderer(Object.class, new SensorRowRenderer());
        sensorTable.setRowHeight(24);
        sensorTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        sensorTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        sensorTable.getColumnModel().getColumn(2).setPreferredWidth(280);
        sensorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                chart.setSeries(tableModel.seriesAt(sensorTable.getSelectedRow()));
            }
        });
        JScrollPane tableScroll = new JScrollPane(sensorTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Sensors"));
        tableScroll.setPreferredSize(new Dimension(400, 130));

        JPanel chartBox = new JPanel(new BorderLayout());
        chartBox.setBorder(BorderFactory.createTitledBorder("History (last 60 readings)"));
        chartBox.add(chart, BorderLayout.CENTER);

        JSplitPane sensorsAndChart = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, chartBox);
        sensorsAndChart.setResizeWeight(0.3);

        actuatorPanel.setLayout(new javax.swing.BoxLayout(actuatorPanel, javax.swing.BoxLayout.Y_AXIS));
        JScrollPane actuatorScroll = new JScrollPane(actuatorPanel);
        actuatorScroll.setBorder(BorderFactory.createTitledBorder("Actuators"));
        actuatorScroll.setPreferredSize(new Dimension(300, 100));

        JPanel details = new JPanel(new BorderLayout());
        details.add(nodeTitle, BorderLayout.NORTH);
        details.add(sensorsAndChart, BorderLayout.CENTER);
        details.add(actuatorScroll, BorderLayout.EAST);

        log.setEditable(false);
        log.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Event log"));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nodeScroll, details);
        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, logScroll);
        all.setResizeWeight(0.8);
        add(all, BorderLayout.CENTER);
    }


    private void showNode(NodeState node) {
        selected = node;
        actuatorPanel.removeAll();
        actuatorStateLabels.clear();
        if (node == null) {
            nodeTitle.setText("Select a node on the left");
            tableModel.setNode(null);
            chart.setSeries(null);
        } else {
            nodeTitle.setText("Node " + node.id());
            tableModel.setNode(node);
            if (tableModel.getRowCount() > 0) {
                sensorTable.setRowSelectionInterval(0, 0);
            } else {
                chart.setSeries(null);
            }
            node.actuatorStates().forEach((actuatorId, state) -> actuatorPanel.add(actuatorRow(node, actuatorId, state)));
        }
        actuatorPanel.revalidate();
        actuatorPanel.repaint();
    }

    private JPanel actuatorRow(NodeState node, String actuatorId, String state) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel name = new JLabel(actuatorId);
        name.setPreferredSize(new Dimension(80, 20));
        JLabel stateLabel = new JLabel();
        stateLabel.setPreferredSize(new Dimension(40, 20));
        styleState(stateLabel, state);
        actuatorStateLabels.put(actuatorId, stateLabel);

        JButton on = new JButton("ON");
        JButton off = new JButton("OFF");
        on.addActionListener(e -> send(node, actuatorId, "ON"));
        off.addActionListener(e -> send(node, actuatorId, "OFF"));
        if (!role.mayControl(actuatorId)) {
            on.setEnabled(false);
            off.setEnabled(false);
            on.setToolTipText("Role " + role + " may not control " + actuatorId);
            off.setToolTipText(on.getToolTipText());
        }
        row.add(name);
        row.add(stateLabel);
        row.add(on);
        row.add(off);
        return row;
    }

    private static void styleState(JLabel label, String state) {
        label.setText(state);
        label.setForeground(state.equals("ON") ? ON_COLOR : Color.GRAY);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
    }

    private void send(NodeState node, String actuatorId, String action) {
        client.sendCommand(node.id(), actuatorId, action);
        log("Sent " + action + " to " + node.id() + "/" + actuatorId);
    }

    private void log(String text) {
        log.append("[" + LocalTime.now().format(TIME) + "] " + text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    // network thread

    @Override
    public void onNodeInfo(String nodeId, String[] sensors, String[] actuators) {
        SwingUtilities.invokeLater(() -> {
            if (nodes.containsKey(nodeId)) {
                return;
            }
            nodes.put(nodeId, new NodeState(nodeId, sensors, actuators));
            int position = 0;
            while (position < nodeListModel.size() && nodeListModel.get(position).compareTo(nodeId) < 0) {
                position++;
            }
            nodeListModel.add(position, nodeId);
            log("Node " + nodeId + " connected (" + sensors.length + " sensors, " + actuators.length + " actuators)");
            if (nodeList.getSelectedIndex() < 0) {
                nodeList.setSelectedIndex(0);
            }
        });
    }

    @Override
    public void onNodeGone(String nodeId) {
        SwingUtilities.invokeLater(() -> {
            NodeState removed = nodes.remove(nodeId);
            if (removed == null) {
                return;
            }
            nodeListModel.removeElement(nodeId);
            log("Node " + nodeId + " disconnected");
            if (removed == selected) {
                showNode(null);
            }
        });
    }

    @Override
    public void onReading(String nodeId, String sensorId, String value) {
        SwingUtilities.invokeLater(() -> {
            NodeState node = nodes.get(nodeId);
            if (node == null) {
                return;
            }
            double parsed;
            try {
                parsed = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log("Ignoring non-numeric reading from " + nodeId + "/" + sensorId + ": " + value);
                return;
            }
            boolean wasAnomaly = node.sensors().stream()
                    .anyMatch(s -> s.id().equals(sensorId) && s.anomaly() != null);
            String anomaly = node.applyReading(sensorId, parsed);
            if (anomaly != null && !wasAnomaly) {
                log("ANOMALY " + nodeId + "/" + sensorId + ": " + anomaly);
            } else if (anomaly == null && wasAnomaly) {
                log("OK again: " + nodeId + "/" + sensorId);
            }
            nodeList.repaint();
            if (node == selected) {
                if (tableModel.getRowCount() > 0) {
                    tableModel.fireTableRowsUpdated(0, tableModel.getRowCount() - 1);
                }
                chart.repaint();
            }
        });
    }

    @Override
    public void onStatus(String nodeId, String actuatorId, String state) {
        SwingUtilities.invokeLater(() -> {
            NodeState node = nodes.get(nodeId);
            if (node == null) {
                return;
            }
            node.applyStatus(actuatorId, state);
            log(nodeId + "/" + actuatorId + " is now " + state);
            if (node == selected && actuatorStateLabels.containsKey(actuatorId)) {
                styleState(actuatorStateLabels.get(actuatorId), state);
            }
        });
    }

    @Override
    public void onError(String code, String text) {
        SwingUtilities.invokeLater(() -> log("Broker error " + code + ": " + text));
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            log(reason);
            JOptionPane.showMessageDialog(this, reason + "\nRestart the panel to reconnect.",
                    "Disconnected", JOptionPane.WARNING_MESSAGE);
        });
    }


    /** Table of the selected node's sensors: id, latest value, status. */
    private static final class SensorTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Sensor", "Value", "Status"};
        private List<SensorSeries> rows = List.of();

        void setNode(NodeState node) {
            rows = node == null ? List.of() : node.sensors();
            fireTableDataChanged();
        }

        SensorSeries seriesAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            SensorSeries series = rows.get(row);
            return switch (column) {
                case 0 -> series.id();
                case 1 -> series.latest() == null ? "-" : String.format(Locale.ROOT, "%.1f", series.latest());
                default -> series.anomaly() == null ? "OK" : series.anomaly();
            };
        }
    }

    /** Colours a table row red while its sensor is in anomaly. */
    private final class SensorRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SensorSeries series = tableModel.seriesAt(row);
            if (!isSelected) {
                c.setForeground(series != null && series.anomaly() != null ? ALERT : table.getForeground());
            }
            return c;
        }
    }

    /** Shows nodes with an active anomaly in red. */
    private final class NodeCellRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            NodeState node = nodes.get(String.valueOf(value));
            boolean alert = node != null && node.hasAnomaly();
            Component c = super.getListCellRendererComponent(list, alert ? value + "  (anomaly)" : value,
                    index, isSelected, cellHasFocus);
            if (alert && !isSelected) {
                c.setForeground(ALERT);
            }
            return c;
        }
    }
}
