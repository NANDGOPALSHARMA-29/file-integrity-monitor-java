import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class AlertBus {

    private static final BlockingQueue<AlertEvent> QUEUE = new LinkedBlockingQueue<>();
    private static final List<java.util.function.Consumer<AlertEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private AlertBus() {}

    public static void register(java.util.function.Consumer<AlertEvent> listener) {
        listeners.add(listener);
    }

    public static void publish(AlertEvent event) {
        if (event != null) {
            QUEUE.offer(event);
            for (java.util.function.Consumer<AlertEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // Ignore listener errors
                }
            }
        }
    }

    public static AlertEvent take() throws InterruptedException {
        return QUEUE.take();
    }

    public static AlertEvent poll(long timeoutMs) throws InterruptedException {
        return QUEUE.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static int drainTo(List<AlertEvent> out) {
        return QUEUE.drainTo(out);
    }

    public static int size() {
        return QUEUE.size();
    }
}
