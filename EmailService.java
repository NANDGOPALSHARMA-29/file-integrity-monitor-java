public final class EmailService {

    public interface ErrorListener {
        void onError(String message);
    }

    private final Object lock = new Object();
    private EmailNotifier notifier;
    private EmailNotifier.Config config;
    private boolean enabled;
    private long generation;
    private final ErrorListener errorListener;

    public EmailService(EmailNotifier.Config config, boolean enabled, ErrorListener errorListener) {
        this.config = config;
        this.enabled = enabled;
        this.errorListener = errorListener;
    }

    public void start() {
        synchronized (lock) {
            if (notifier != null) return;
            notifier = new EmailNotifier(
                    () -> snapshot(),
                    msg -> {
                        if (errorListener != null) {
                            errorListener.onError(msg);
                        }
                    }
            );
            notifier.start();
        }
    }

    public void stop() {
        synchronized (lock) {
            if (notifier != null) {
                notifier.stop();
                notifier = null;
            }
        }
    }

    public void setEnabled(boolean enabled) {
        synchronized (lock) {
            if (this.enabled == enabled) return;
            this.enabled = enabled;
            generation++;
        }
    }

    public void updateConfig(EmailNotifier.Config config) {
        synchronized (lock) {
            this.config = config;
            generation++;
        }
    }

    public boolean isEnabled() {
        synchronized (lock) {
            return enabled;
        }
    }

    private EmailNotifier.ConfigSnapshot snapshot() {
        synchronized (lock) {
            return new EmailNotifier.ConfigSnapshot(config, enabled, generation);
        }
    }
}
