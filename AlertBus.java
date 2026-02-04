import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class AlertBus {

    private static final BlockingQueue<AlertEvent> QUEUE = new LinkedBlockingQueue<>();

    private AlertBus() {}

    public static void publish(AlertEvent event) {
        if (event != null) {
            QUEUE.offer(event);
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
