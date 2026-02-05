import java.nio.file.Path;
import java.util.function.Consumer;

public final class MonitorSession {

    private final Object lock = new Object();
    private Thread thread;
    private volatile boolean running;

    public MonitorSession() {}

    public boolean isRunning() {
        return running;
    }

    public boolean start(Path path, Consumer<Exception> onError, Runnable onFinish) {
        synchronized (lock) {
            if (running) return false;
            running = true;
        }

        thread = new Thread(() -> {
            try {
                Monitor.start(path);
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e);
                }
            } finally {
                synchronized (lock) {
                    running = false;
                }
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        }, "fim-monitor");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public boolean stopAndWait(long timeoutMs) {
        Monitor.stop();
        Thread t = thread;
        if (t == null) return true;
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !t.isAlive();
    }
}
