import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public class Gui {

    private static final String DEFAULT_PATH = System.getProperty("user.home");

    public static void main(String[] args) {
        EventQueue.invokeLater(Gui::buildUi);
    }

    private static void buildUi() {
        JFrame frame = new JFrame("FIM Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField pathField = new JTextField(DEFAULT_PATH);
        pathField.setPreferredSize(new Dimension(260, 28));

        JButton browse = new JButton("Browse");
        JButton createBaseline = new JButton("Create Baseline");
        JButton checkIntegrity = new JButton("Check Integrity");
        JButton startMonitoring = new JButton("Start Monitoring");
        JButton stopMonitoring = new JButton("Stop Monitoring");
        stopMonitoring.setEnabled(false);

        JCheckBox emailEnabled = new JCheckBox("Email Enabled");
        JTextField batchSec = new JTextField(5);
        JTextField attachMax = new JTextField(8);

        JTextArea log = new JTextArea(10, 40);
        log.setEditable(false);
        JScrollPane scroll = new JScrollPane(log);

        JPanel top = new JPanel();
        top.add(new JLabel("Folder:"));
        top.add(pathField);
        top.add(browse);

        JPanel actions = new JPanel();
        actions.add(createBaseline);
        actions.add(checkIntegrity);
        actions.add(startMonitoring);
        actions.add(stopMonitoring);

        JPanel emailPanel = new JPanel();
        emailPanel.add(emailEnabled);
        emailPanel.add(new JLabel("Batch (sec):"));
        emailPanel.add(batchSec);
        emailPanel.add(new JLabel("Attach max (bytes):"));
        emailPanel.add(attachMax);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(actions);
        center.add(emailPanel);

        JLabel status = new JLabel("Monitor: Stopped | Email: Disabled");

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
                        JOptionPane.ERROR_MESSAGE
                ));
            }

            @Override
            public void showInfo(String message) {
                runOnEdt(() -> JOptionPane.showMessageDialog(
                        frame,
                        message,
                        "FIM",
                        JOptionPane.INFORMATION_MESSAGE
                ));
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
        });

        AppLog.setSink((level, message) -> runOnEdt(() -> {
            log.append(message + System.lineSeparator());
            log.setCaretPosition(log.getDocument().getLength());
        }));
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
                JOptionPane.WARNING_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }

    private static void applyEmailSettings(
            GuiController controller,
            JTextField batchSec,
            JTextField attachMax,
            JFrame frame
    ) {
        Long b = parseLong(batchSec.getText());
        Long a = parseLong(attachMax.getText());
        if (b == null || a == null || b <= 0 || a <= 0) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Please enter valid positive numbers for email settings.",
                    "FIM",
                    JOptionPane.ERROR_MESSAGE
            );
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
        if (monitor != null) monitorText = monitor;
        if (email != null) emailText = email;
        label.setText(monitorText + " | " + emailText);
    }
}
