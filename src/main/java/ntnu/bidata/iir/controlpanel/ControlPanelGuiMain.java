package ntnu.bidata.iir.controlpanel;

import java.awt.GridLayout;
import java.io.IOException;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ntnu.bidata.iir.common.Role;

/**
 * Entry point of the GUI control panel: shows a login dialog, then the main window.
 * Demo passwords are defined in the broker's Authenticator class.
 */
public final class ControlPanelGuiMain {
    private ControlPanelGuiMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ControlPanelGuiMain::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JTextField host = new JTextField("localhost");
        JTextField panelId = new JTextField("panel1");
        JComboBox<Role> role = new JComboBox<>(Role.values());
        role.setSelectedItem(Role.OWNER);
        JPasswordField password = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Broker host"));
        form.add(host);
        form.add(new JLabel("Panel id"));
        form.add(panelId);
        form.add(new JLabel("Role"));
        form.add(role);
        form.add(new JLabel("Password"));
        form.add(password);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(null, form, "SHA Control Panel - Login",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                System.exit(0);
            }
            Role selectedRole = (Role) role.getSelectedItem();
            ControlPanelGui gui = new ControlPanelGui(panelId.getText().trim(), selectedRole);
            try {
                PanelClient client = PanelClient.connect(host.getText().trim(), panelId.getText().trim(),
                        selectedRole, new String(password.getPassword()), gui);
                gui.attach(client);
                gui.setVisible(true);
                return;
            } catch (IOException e) {
                gui.dispose();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Could not log in", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
