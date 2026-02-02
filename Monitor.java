import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public class Monitor {

    private static WatchService watchService;

    // WatchKey → Directory
    private static final Map<WatchKey, Path> keyDirMap =
            new ConcurrentHashMap<>();

    // Immutable baseline
    private static final Map<String, String> baselineDisk =
            new ConcurrentHashMap<>();

    // Runtime state (GROUND TRUTH)
    private static final Map<String, String> runtimeState =
            new ConcurrentHashMap<>();

    // MODIFY debounce
    private static final Map<String, Long> lastEventTime =
            new ConcurrentHashMap<>();

    // Pending file deletes
    private static final Map<String, Long> pendingDeletes =
            new ConcurrentHashMap<>();

    // Pending folder renames (oldPath → time)
    private static final Map<String, Long> pendingRenames =
            new ConcurrentHashMap<>();

    private static final long DEBOUNCE_MS = 300;
    private static final long DELETE_VERIFY_MS = 300;
    private static final long RENAME_WINDOW_MS = 800;

    // ─────────────────────────────────────

    public static void start(Path rootDir) throws Exception {

        watchService = FileSystems.getDefault().newWatchService();
        String rootPath = rootDir.toFile().getCanonicalPath();

        baselineDisk.clear();
        runtimeState.clear();
        pendingDeletes.clear();
        pendingRenames.clear();

        Map<String, String> loaded =
                normalizeBaseline(FIM.loadBaselineForMonitor());

        baselineDisk.putAll(loaded);
        runtimeState.putAll(loaded); // baseline = runtime at start

        registerAll(rootDir);

        System.out.println("[+] Real-time FIM started");
        System.out.println("[+] Root: " + rootPath);

        // ───── MAIN LOOP ─────
        while (true) {

            WatchKey key = watchService.poll(200, TimeUnit.MILLISECONDS);

            if (key == null) {
                cleanupDeletes(rootPath);
                continue;
            }

            Path dir = keyDirMap.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {

                if (event.kind() == OVERFLOW) continue;

                Path name = (Path) event.context();
                File file = dir.resolve(name).toFile();

                if (event.kind() == ENTRY_CREATE && file.isDirectory()) {
                    registerAll(file.toPath());
                }

                handleEvent(event.kind(), file, rootPath);
            }

            cleanupDeletes(rootPath);

            if (!key.reset()) {
                keyDirMap.remove(key);
            }
        }
    }

    // ─────────────────────────────────────

    private static void register(Path dir) throws Exception {
        WatchKey key = dir.register(
                watchService,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY
        );
        keyDirMap.put(key, dir);
    }

    private static void registerAll(Path start) throws Exception {
        Files.walk(start)
                .filter(Files::isDirectory)
                .forEach(p -> {
                    try { register(p); } catch (Exception ignored) {}
                });
    }

    // ─────────────────────────────────────

    private static void handleEvent(
            WatchEvent.Kind<?> kind,
            File file,
            String rootPath
    ) {

        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path absPath;

        try {
            absPath = file.toPath().toAbsolutePath().normalize();
        } catch (Exception e) {
            return;
        }

        if (!absPath.startsWith(root)) return;

        String relPath = root.relativize(absPath)
                .toString()
                .replace(File.separatorChar, '/');

        // ───── DIRECTORY HANDLING ─────
        boolean isDir =
                runtimeState.containsKey(relPath)
                        && "DIR".equals(runtimeState.get(relPath));

        if (isDir || (kind == ENTRY_CREATE && file.isDirectory())) {

            // CREATE → new folder or rename target
            if (kind == ENTRY_CREATE) {

                String parent = Paths.get(relPath).getParent() == null
                        ? ""
                        : Paths.get(relPath).getParent().toString();

                String renamedFrom = null;
                long now = System.currentTimeMillis();

                for (Map.Entry<String, Long> e : pendingRenames.entrySet()) {

                    String oldPath = e.getKey();
                    Path oldParent = Paths.get(oldPath).getParent();

                    if (!Objects.equals(
                            oldParent == null ? "" : oldParent.toString(),
                            parent
                    )) continue;

                    if (now - e.getValue() > RENAME_WINDOW_MS) continue;

                    renamedFrom = oldPath;
                    break;
                }

                if (renamedFrom != null) {
                    pendingRenames.remove(renamedFrom);
                    runtimeState.remove(renamedFrom);
                    runtimeState.put(relPath, "DIR");
                    System.out.println("[RENAMED] " + renamedFrom + " → " + relPath);
                } else {
                    runtimeState.put(relPath, "DIR");
                    System.out.println("[NEW FOLDER] " + relPath);
                }
                return;
            }

            // DELETE → maybe rename
            if (kind == ENTRY_DELETE) {
                pendingRenames.put(relPath, System.currentTimeMillis());
                return;
            }

            return;
        }

        // ───── TEMP FILE FILTER ─────
        String name = file.getName();
        if (name.startsWith("~")
                || name.endsWith(".tmp")
                || name.endsWith(".swp")
                || name.endsWith(".bak")) {
            return;
        }

        // ───── FILE DELETE (DELAYED) ─────
        if (kind == ENTRY_DELETE) {
            pendingDeletes.put(relPath, System.currentTimeMillis());
            return;
        }

        // recreate → cancel delete
        pendingDeletes.remove(relPath);

        if (!file.exists()) return;

        // ───── MODIFY DEBOUNCE ─────
        long now = System.currentTimeMillis();
        Long last = lastEventTime.get(relPath);

        if (last != null && now - last < DEBOUNCE_MS) return;
        lastEventTime.put(relPath, now);

        // ───── HASH ─────
        String newHash;
        try {
            newHash = FIM.getFileHash(file);
        } catch (Exception e) {
            return;
        }

        String oldRuntime = runtimeState.get(relPath);
        String baseHash  = baselineDisk.get(relPath);

        // CREATE
        if (oldRuntime == null) {
            runtimeState.put(relPath, newHash);
            System.out.println("[NEW FILE] " + relPath);
            return;
        }

        // MODIFY
        if (!newHash.equals(oldRuntime)) {
            runtimeState.put(relPath, newHash);
            if (baseHash != null && baseHash.equals(newHash)) {
                System.out.println("[RESTORED] " + relPath);
            } else {
                System.out.println("[MODIFIED] " + relPath);
            }
        }
    }

    // ─────────────────────────────────────

    private static void cleanupDeletes(String rootPath) {

        long now = System.currentTimeMillis();
        Path root = Paths.get(rootPath);

        // FILE deletes
        pendingDeletes.entrySet().removeIf(e -> {

            if (now - e.getValue() < DELETE_VERIFY_MS)
                return false;

            String path = e.getKey();
            File f = root.resolve(path).toFile();

            if (!f.exists() && runtimeState.containsKey(path)) {
                runtimeState.remove(path);
                System.out.println("[DELETED FILE] " + path);
            }
            return true;
        });

        // FOLDER deletes (not renames)
        pendingRenames.entrySet().removeIf(e -> {

            if (now - e.getValue() < RENAME_WINDOW_MS)
                return false;

            String path = e.getKey();
            String prefix = path + "/";

            runtimeState.keySet().removeIf(p ->
                    p.equals(path) || p.startsWith(prefix)
            );

            System.out.println("[DELETED FOLDER] " + path);
            return true;
        });
    }

    // ─────────────────────────────────────

    private static Map<String, String> normalizeBaseline(
            Map<String, String> input
    ) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            out.put(
                    e.getKey().replace(File.separatorChar, '/'),
                    e.getValue()
            );
        }
        return out;
    }
}



