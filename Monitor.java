import java.io.File;
import java.nio.file.*;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class Monitor {

    public static void start(Path folderPath, String rootPath) throws Exception {

        WatchService watchService = FileSystems.getDefault().newWatchService();

        folderPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        System.out.println("[+] Real-time monitoring started");

        while (true) {
            WatchKey key = watchService.take();

            for (WatchEvent<?> event : key.pollEvents()) {

                WatchEvent.Kind<?> kind = event.kind();
                Path changed = (Path) event.context();
                File file = folderPath.resolve(changed).toFile();

                handleEvent(kind, file, rootPath);
            }

            key.reset();
        }
    }

    private static void handleEvent(WatchEvent.Kind<?> kind, File file, String rootPath)
            throws Exception {

        Map<String, String> baseline = FIM.loadBaseline();

        String relPath = file.getCanonicalPath().substring(rootPath.length());

        if (kind == ENTRY_DELETE) {
            if (baseline.containsKey(relPath)) {
                System.out.println("[DELETED] " + relPath);
            }
            return;
        }

        if (!file.exists()) return;

        String newHash = FIM.getFileHash(file);
        String oldHash = baseline.get(relPath);

        if (oldHash == null) {
            System.out.println("[NEW FILE] " + relPath);
        } 
        else if (!oldHash.equals(newHash)) {
            System.out.println("[MODIFIED] " + relPath);
        }
    }
}


