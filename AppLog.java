public final class AppLog {

    public enum Level {
        INFO,
        WARN,
        ERROR
    }

    public interface Sink {
        void log(Level level, String message);
    }

    private static volatile Sink sink;

    private AppLog() {}

    public static void setSink(Sink s) {
        sink = s;
    }

    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    public static void warn(String msg) {
        log(Level.WARN, msg);
    }

    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    private static void log(Level level, String msg) {
        Sink s = sink;
        if (s != null) {
            s.log(level, msg);
        }
        if (level == Level.ERROR) {
            System.err.println(msg);
        } else {
            System.out.println(msg);
        }
    }
}
