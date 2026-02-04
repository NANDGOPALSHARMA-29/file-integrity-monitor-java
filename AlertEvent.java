import java.time.Instant;

public final class AlertEvent {

    public enum Type {
        NEW_FILE,
        NEW_FOLDER,
        MODIFIED,
        RESTORED,
        DELETED_FILE,
        DELETED_FOLDER,
        RENAMED_FILE,
        MOVED_FILE,
        RENAMED_FOLDER
    }

    public final Type type;
    public final String path;
    public final String oldPath;
    public final String absolutePath;
    public final boolean isDirectory;
    public final Instant timestamp;

    private AlertEvent(
            Type type,
            String path,
            String oldPath,
            String absolutePath,
            boolean isDirectory,
            Instant timestamp
    ) {
        this.type = type;
        this.path = path;
        this.oldPath = oldPath;
        this.absolutePath = absolutePath;
        this.isDirectory = isDirectory;
        this.timestamp = timestamp;
    }

    public static AlertEvent of(
            Type type,
            String path,
            String oldPath,
            String absolutePath,
            boolean isDirectory
    ) {
        return new AlertEvent(type, path, oldPath, absolutePath, isDirectory, Instant.now());
    }
}
