import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public final class GuiController {

    public interface View {
        void showError(String message);
        void showInfo(String message);
        boolean confirmBaselineCreate(File folder);
        void setMonitoringState(boolean running);
        void setMonitorStatus(String text);
        void setEmailStatus(String text);
        void addEvent(AlertEvent event);
    }

    private final View view;
    private final MonitorSession session;
    private final GuiConfig guiConfig;
    private final EmailService emailService;
    private boolean emailDesiredEnabled;

    public GuiController(View view) {
        this.view = view;
        this.session = new MonitorSession();
        this.guiConfig = GuiConfig.fromEnvDefaults();
        this.emailDesiredEnabled = guiConfig.isEmailEnabled();
        this.emailService = new EmailService(
                guiConfig.buildEmailConfig(),
                false,
                msg -> view.showError("Email error: " + msg)
        );
    }

    public void startApp() {
        emailService.start();
        view.setMonitorStatus("Monitor: Stopped");
        view.setEmailStatus("Email: " + (emailDesiredEnabled ? "Enabled" : "Disabled"));

        // Subscribe to live events
        AlertBus.register(event -> view.addEvent(event));
    }

    public void shutdown() {
        session.stopAndWait(TimeUnit.SECONDS.toMillis(2));
        emailService.stop();
    }

    public void createBaseline(String pathText) {
        File folder = validateFolder(pathText);
        if (folder == null) return;
        new Thread(() -> {
            try {
                FIM.rootPath = folder.getCanonicalPath();
                FIM.createBaseline(folder);
                AppLog.info("[+] Baseline created successfully.");
            } catch (Exception ex) {
                view.showError("Baseline failed: " + ex.getMessage());
            }
        }, "fim-baseline").start();
    }

    public void checkIntegrity(String pathText) {
        File folder = validateFolder(pathText);
        if (folder == null) return;
        new Thread(() -> {
            try {
                FIM.rootPath = folder.getCanonicalPath();
                FIM.checkIntegrity(folder);
            } catch (Exception ex) {
                view.showError("Integrity check failed: " + ex.getMessage());
            }
        }, "fim-integrity").start();
    }

    public void startMonitoring(String pathText) {
        if (session.isRunning()) {
            view.showInfo("Monitoring is already running.");
            return;
        }

        File folder = validateFolder(pathText);
        if (folder == null) return;

        new Thread(() -> {
            try {
                FIM.rootPath = folder.getCanonicalPath();

                if (!FIM.getBaselineFile().exists()) {
                    if (!view.confirmBaselineCreate(folder)) {
                        return;
                    }
                    FIM.createBaseline(folder);
                    AppLog.info("[+] Baseline created.");
                }

                boolean started = session.start(
                        Paths.get(folder.getAbsolutePath()),
                        ex -> view.showError("Monitor failed: " + ex.getMessage()),
                        () -> view.setMonitoringState(false)
                );

                if (started) {
                    refreshEmailEnabled();
                    view.setMonitoringState(true);
                    view.setMonitorStatus("Monitor: Running");
                } else {
                    refreshEmailEnabled();
                    view.showInfo("Monitoring is already running.");
                }
            } catch (Exception ex) {
                view.showError("Monitor failed: " + ex.getMessage());
                view.setMonitoringState(false);
                refreshEmailEnabled();
            }
        }, "fim-gui-monitor").start();
    }

    public void stopMonitoring() {
        if (!session.isRunning()) {
            view.showInfo("Monitoring is not running.");
            return;
        }
        AppLog.warn("[!] Stop requested.");
        boolean stopped = session.stopAndWait(TimeUnit.SECONDS.toMillis(2));
        refreshEmailEnabled();
        if (!stopped) {
            view.showError("Monitor did not stop within timeout.");
        } else {
            view.setMonitorStatus("Monitor: Stopped");
        }
    }

    public void setEmailEnabled(boolean enabled) {
        emailDesiredEnabled = enabled;
        refreshEmailEnabled();
        view.setEmailStatus("Email: " + (emailDesiredEnabled ? "Enabled" : "Disabled"));
    }

    public void updateEmailSettings(long batchSec, long attachMaxBytes) {
        guiConfig.setBatchSec(batchSec);
        guiConfig.setAttachMaxBytes(attachMaxBytes);
        emailService.updateConfig(guiConfig.buildEmailConfig());
        AppLog.info("[Email] Settings updated.");
    }

    private void refreshEmailEnabled() {
        boolean effective = emailDesiredEnabled && session.isRunning();
        emailService.setEnabled(effective);
    }

    private File validateFolder(String pathText) {
        File folder = new File(pathText.trim());
        if (!folder.exists() || !folder.isDirectory()) {
            view.showError("Invalid folder path: " + folder.getAbsolutePath()
                    + "\nPlease select an existing folder.");
            return null;
        }
        return folder;
    }
}
