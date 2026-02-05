import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class Gui {

    private static final String DEFAULT_PATH = System.getProperty("user.home");

    public static void main(String[] args) {
        Theme.applyDarkTheme();
        EventQueue.invokeLater(Gui::buildUi);
    }

    private static void buildUi() {
        JFrame frame = new JFrame("FIM Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField pathField = new JTextField(DEFAULT_PATH);
        pathField.setPreferredSize(new Dimension(300, 35));

        // Use ModernButton for all actions
        ModernButton browse = new ModernButton("Browse");
        ModernButton createBaseline = new ModernButton("Create Baseline");
        ModernButton checkIntegrity = new ModernButton("Check Integrity");
        ModernButton startMonitoring = new ModernButton("Start Monitoring");
        ModernButton stopMonitoring = new ModernButton("Stop Monitoring");
        stopMonitoring.setEnabled(false);

        JCheckBox emailEnabled = new JCheckBox("Email Enabled");
        JTextField batchSec = new JTextField(5);
        JTextField attachMax = new JTextField(8);

        // Table Setup
        EventTableModel model = new EventTableModel();
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(30);
        table.getTableHeader().setPreferredSize(new Dimension(0, 35));

        // Custom Renderer (Color Coding)
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                AlertEvent event = model.getEventAt(row);

                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                } else {
                    c.setForeground(Theme.FG_TEXT); // Default Text Color
                    if (event != null) {
                        switch (event.type) {
                            case DELETED_FILE, DELETED_FOLDER -> {
                                c.setBackground(Theme.COLOR_DELETED);
                                c.setForeground(Color.WHITE); // White text on dark red
                            }
                            case NEW_FILE, NEW_FOLDER -> {
                                c.setBackground(Theme.COLOR_NEW);
                                c.setForeground(Color.WHITE);
                            }
                            case MODIFIED -> {
                                c.setBackground(Theme.COLOR_MODIFIED);
                                c.setForeground(Color.WHITE);
                            }
                            case RESTORED -> {
                                c.setBackground(Theme.COLOR_RESTORED);
                                c.setForeground(Color.WHITE);
                            }
                            default -> c.setBackground(Theme.BG_DARK);
                        }
                    } else {
                        c.setBackground(Theme.BG_DARK);
                    }
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(800, 300)); // Adjusted height

        // --- LAYOUT ---

        // Top Bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        top.add(new JLabel("Folder to Monitor:"));
        top.add(pathField);
        top.add(browse);

        // Action Buttons Bar
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        actions.add(createBaseline);
        actions.add(checkIntegrity);
        actions.add(startMonitoring);
        actions.add(stopMonitoring);

        // Email Settings Bar
        JPanel emailPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        emailPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Email Settings",
                0, 0, Theme.FONT_MAIN, Theme.FG_BRIGHT));
        emailPanel.add(emailEnabled);
        emailPanel.add(new JLabel("Batch (sec):"));
        emailPanel.add(batchSec);
        emailPanel.add(new JLabel("Attach max (bytes):"));
        emailPanel.add(attachMax);

        // Combine Actions and Email
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // Breathing room
        center.add(actions);
        center.add(Box.createVerticalStrut(10));
        center.add(emailPanel);
        center.add(Box.createVerticalStrut(10));

        JLabel status = new JLabel("Monitor: Stopped | Email: Disabled");
        status.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        status.setFont(Theme.FONT_MAIN_BOLD);

        frame.add(top, BorderLayout.NORTH);
        frame.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(scroll, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        GuiController controller = new GuiController(new GuiController.View() {
            @Override
            public void showError(String message) {
                runOnEdt(() -> JOptionPane.showMessageDialog(
                        frame,
                        message,
                        "FIM",
                        JOptionPane.ERROR_MESSAGE));
            }

            @Override
            public void showInfo(String message) {
                runOnEdt(() -> JOptionPane.showMessageDialog(
                        frame,
                        message,
                        "FIM",
                        JOptionPane.INFORMATION_MESSAGE));
            }

            @Override
            public boolean confirmBaselineCreate(File folder) {
                if (SwingUtilities.isEventDispatchThread()) {
                    return confirmOnEdt(frame, folder);
                }
                final boolean[] result = new boolean[1];
                try {
                    SwingUtilities.invokeAndWait(() -> result[0] = confirmOnEdt(frame, folder));
                } catch (Exception e) {
                    return false;
                }
                return result[0];
            }

            @Override
            public void setMonitoringState(boolean running) {
                runOnEdt(() -> {
                    startMonitoring.setEnabled(!running);
                    stopMonitoring.setEnabled(running);
                    browse.setEnabled(!running);
                    pathField.setEnabled(!running);
                });
            }

            @Override
            public void setMonitorStatus(String text) {
                runOnEdt(() -> updateStatus(status, text, null));
            }

            @Override
            public void setEmailStatus(String text) {
                runOnEdt(() -> updateStatus(status, null, text));
            }

            @Override
            public void addEvent(AlertEvent event) {
                runOnEdt(() -> model.addEvent(event));
            }
        });

        // AppLog.setSink removed effectively because we want Table to be the main view.
        // But for console debugging we keep default System.out
        controller.startApp();

        GuiConfig defaults = GuiConfig.fromEnvDefaults();
        emailEnabled.setSelected(defaults.isEmailEnabled());
        batchSec.setText(String.valueOf(defaults.getBatchSec()));
        attachMax.setText(String.valueOf(defaults.getAttachMaxBytes()));
        controller.setEmailEnabled(defaults.isEmailEnabled());

        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File(pathField.getText()));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        createBaseline.addActionListener(e -> controller.createBaseline(pathField.getText()));
        checkIntegrity.addActionListener(e -> controller.checkIntegrity(pathField.getText()));
        startMonitoring.addActionListener(e -> controller.startMonitoring(pathField.getText()));
        stopMonitoring.addActionListener(e -> controller.stopMonitoring());

        emailEnabled.addActionListener(e -> controller.setEmailEnabled(emailEnabled.isSelected()));

        batchSec.addActionListener(e -> applyEmailSettings(controller, batchSec, attachMax, frame));
        attachMax.addActionListener(e -> applyEmailSettings(controller, batchSec, attachMax, frame));

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                new Thread(controller::shutdown, "fim-shutdown").start();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static boolean confirmOnEdt(JFrame frame, File folder) {
        int result = JOptionPane.showConfirmDialog(
                frame,
                "Baseline not found for:\n" + folder.getAbsolutePath() + "\n\nCreate baseline now?",
                "Create Baseline",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    private static void applyEmailSettings(
            GuiController controller,
            JTextField batchSec,
            JTextField attachMax,
            JFrame frame) {
        Long b = parseLong(batchSec.getText());
        Long a = parseLong(attachMax.getText());
        if (b == null || a == null || b <= 0 || a <= 0) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Please enter valid positive numbers for email settings.",
                    "FIM",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        controller.updateEmailSettings(b, a);
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static void updateStatus(JLabel label, String monitor, String email) {
        String text = label.getText();
        String[] parts = text.split("\\|", -1);
        String monitorText = parts.length > 0 ? parts[0].trim() : "Monitor: Stopped";
        String emailText = parts.length > 1 ? parts[1].trim() : "Email: Disabled";
        if (monitor != null)
            monitorText = monitor;
        if (email != null)
            emailText = email;
        label.setText(monitorText + " | " + emailText);
    }
}
