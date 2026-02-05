import java.nio.file.Path;

public final class GuiConfig {

    private Path rootPath;
    private boolean emailEnabled;
    private long batchSec;
    private long attachMaxBytes;

    public GuiConfig() {}

    public static GuiConfig fromEnvDefaults() {
        EmailNotifier.Config base = EmailNotifier.Config.fromEnv();
        GuiConfig cfg = new GuiConfig();
        cfg.emailEnabled = !base.smtpHost.isEmpty();
        cfg.batchSec = Math.max(1, base.batchWindowMs / 1000);
        cfg.attachMaxBytes = Math.max(1, base.attachMaxBytes);
        return cfg;
    }

    public EmailNotifier.Config buildEmailConfig() {
        return EmailNotifier.Config.fromEnvWithOverrides(
                Boolean.valueOf(emailEnabled),
                Long.valueOf(batchSec),
                Long.valueOf(attachMaxBytes)
        );
    }

    public Path getRootPath() {
        return rootPath;
    }

    public void setRootPath(Path rootPath) {
        this.rootPath = rootPath;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public long getBatchSec() {
        return batchSec;
    }

    public void setBatchSec(long batchSec) {
        this.batchSec = batchSec;
    }

    public long getAttachMaxBytes() {
        return attachMaxBytes;
    }

    public void setAttachMaxBytes(long attachMaxBytes) {
        this.attachMaxBytes = attachMaxBytes;
    }
}
