import java.io.File;
import java.io.IOException;
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

    // Pending file renames (oldPath → hash/time/parent)
    private static final Map<String, PendingFileRename> pendingFileRenames =
            new ConcurrentHashMap<>();

    // Pending folder renames (oldPath → time)
    private static final Map<String, Long> pendingRenames =
            new ConcurrentHashMap<>();

    private static final long DEBOUNCE_MS = 300;
    private static final long DELETE_VERIFY_MS = 300;
    private static final long RENAME_WINDOW_MS = 1200;

    private static volatile boolean running = true;

    // ─────────────────────────────────────

    public static void start(Path rootDir) throws Exception {

        watchService = FileSystems.getDefault().newWatchService();
        String rootPath = rootDir.toFile().getCanonicalPath();

        baselineDisk.clear();
        runtimeState.clear();
        pendingDeletes.clear();
        pendingFileRenames.clear();
        pendingRenames.clear();

        Map<String, String> loaded =
                normalizeBaseline(FIM.loadBaselineForMonitor());

        baselineDisk.putAll(loaded);
        runtimeState.putAll(loaded); // baseline = runtime at start

        registerAll(rootDir);

        Runtime.getRuntime().addShutdownHook(new Thread(Monitor::stop));

        System.out.println("[+] Real-time FIM started");
        System.out.println("[+] Root: " + rootPath);

        // ───── MAIN LOOP ─────
        while (running) {

            WatchKey key;
            try {
                key = watchService.poll(200, TimeUnit.MILLISECONDS);
            } catch (ClosedWatchServiceException e) {
                break;
            }

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

        stop();
    }

    public static void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
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
                        && FIM.DIR_HASH.equals(runtimeState.get(relPath));

        if (isDir || (kind == ENTRY_CREATE && file.isDirectory())) {

            // CREATE → new folder or rename target
            if (kind == ENTRY_CREATE) {

                String parent = Paths.get(relPath).getParent() == null
                        ? ""
                        : Paths.get(relPath).getParent().toString();

                long now = System.currentTimeMillis();
                String renamedFrom = findFolderRenameCandidate(parent, now);

                if (renamedFrom != null) {
                    pendingRenames.remove(renamedFrom);
                    remapRuntimeSubtree(renamedFrom, relPath);
                    System.out.println("[RENAMED] " + renamedFrom + " → " + relPath);
                } else {
                    runtimeState.put(relPath, FIM.DIR_HASH);
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
            long now = System.currentTimeMillis();
            if (looksLikeDirectory(relPath)) {
                pendingRenames.put(relPath, now);
                return;
            }

            String oldRuntime = runtimeState.get(relPath);
            if (oldRuntime != null && !FIM.DIR_HASH.equals(oldRuntime)) {
                pendingFileRenames.put(
                        relPath,
                        new PendingFileRename(
                                oldRuntime,
                                now,
                                parentOf(relPath)
                        )
                );
            }

            pendingDeletes.put(relPath, now);
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

        String renamedFrom = findFileRenameCandidate(relPath, newHash, now);
        if (renamedFrom != null) {
            PendingFileRename meta = pendingFileRenames.remove(renamedFrom);
            pendingDeletes.remove(renamedFrom);
            runtimeState.remove(renamedFrom);
            runtimeState.put(relPath, newHash);

            String oldParent = meta == null ? "" : meta.parent;
            String newParent = parentOf(relPath);
            if (Objects.equals(oldParent, newParent)) {
                System.out.println("[RENAMED FILE] " + renamedFrom + " → " + relPath);
            } else {
                System.out.println("[MOVED FILE] " + renamedFrom + " → " + relPath);
            }
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

    private static String parentOf(String relPath) {
        Path p = Paths.get(relPath).getParent();
        return p == null ? "" : p.toString();
    }

    private static String findFolderRenameCandidate(String parent, long now) {
        for (Map.Entry<String, Long> e : pendingRenames.entrySet()) {
            String oldPath = e.getKey();
            if (now - e.getValue() > RENAME_WINDOW_MS) continue;
            if (Objects.equals(parentOf(oldPath), parent)) {
                return oldPath;
            }
        }

        String candidate = null;
        for (Map.Entry<String, Long> e : pendingRenames.entrySet()) {
            if (now - e.getValue() > RENAME_WINDOW_MS) continue;
            if (candidate != null) return null;
            candidate = e.getKey();
        }
        return candidate;
    }

    private static String findFileRenameCandidate(
            String newPath,
            String newHash,
            long now
    ) {
        String parent = parentOf(newPath);
        String best = null;
        long bestTime = -1;

        for (Map.Entry<String, PendingFileRename> e : pendingFileRenames.entrySet()) {
            PendingFileRename p = e.getValue();
            if (now - p.time > RENAME_WINDOW_MS) continue;
            if (!p.hash.equals(newHash)) continue;
            if (Objects.equals(p.parent, parent) && p.time > bestTime) {
                best = e.getKey();
                bestTime = p.time;
            }
        }

        if (best != null) return best;

        String candidate = null;
        for (Map.Entry<String, PendingFileRename> e : pendingFileRenames.entrySet()) {
            PendingFileRename p = e.getValue();
            if (now - p.time > RENAME_WINDOW_MS) continue;
            if (!p.hash.equals(newHash)) continue;
            if (candidate != null) return null;
            candidate = e.getKey();
        }
        return candidate;
    }

    private static void cleanupDeletes(String rootPath) {

        long now = System.currentTimeMillis();
        Path root = Paths.get(rootPath);

        pendingFileRenames.entrySet().removeIf(e ->
                now - e.getValue().time > RENAME_WINDOW_MS
        );

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

    private static void remapRuntimeSubtree(String oldPath, String newPath) {

        String oldPrefix = oldPath + "/";
        String newPrefix = newPath + "/";

        Map<String, String> toAdd = new HashMap<>();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, String> e : runtimeState.entrySet()) {
            String key = e.getKey();

            if (key.equals(oldPath)) {
                toRemove.add(key);
                toAdd.put(newPath, e.getValue());
                continue;
            }

            if (key.startsWith(oldPrefix)) {
                String suffix = key.substring(oldPrefix.length());
                toRemove.add(key);
                toAdd.put(newPrefix + suffix, e.getValue());
            }
        }

        for (String k : toRemove) runtimeState.remove(k);
        runtimeState.putAll(toAdd);

        // Clear any pending deletes under old path to avoid false deletes
        pendingDeletes.keySet().removeIf(k ->
                k.equals(oldPath) || k.startsWith(oldPrefix)
        );
    }

    private static final class PendingFileRename {
        final String hash;
        final long time;
        final String parent;

        PendingFileRename(String h, long t, String p) {
            hash = h;
            time = t;
            parent = p;
        }
    }

    private static boolean looksLikeDirectory(String relPath) {
        if (runtimeState.containsKey(relPath)
                && FIM.DIR_HASH.equals(runtimeState.get(relPath))) {
            return true;
        }
        String prefix = relPath + "/";
        for (String key : runtimeState.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

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

