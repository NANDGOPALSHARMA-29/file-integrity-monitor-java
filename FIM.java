import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class FIM {

    static String rootPath;
    static final String DIR_HASH = "DIR";
    static final String UNREADABLE_HASH = "UNREADABLE";

    // ---------- MAIN ----------

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter folder path: ");
        String folderPath = sc.nextLine();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            AppLog.error("Invalid folder path: " + folderPath);
            return;
        }

        try {
            rootPath = folder.getCanonicalPath();
        } catch (IOException e) {
            AppLog.error("Failed to resolve folder path.");
            return;
        }

        AppLog.info("\n1. Create Baseline");
        AppLog.info("2. Check Integrity");
        AppLog.info("3. Start Real-Time Monitoring");
        AppLog.info("4. Update Baseline");
        System.out.print("Choose option: ");

        int choice;
        try {
            choice = Integer.parseInt(sc.nextLine());
        } catch (Exception e) {
            AppLog.error("Invalid choice. Please enter 1-4.");
            return;
        }

        try {
            switch (choice) {
                case 1 -> {
                    createBaseline(folder);
                    AppLog.info("\n[+] Baseline created successfully.");
                }
                case 2 -> checkIntegrity(folder);
                case 3 -> {
                    if (!getBaselineFile().exists()) {
                        AppLog.warn("[!] Baseline not found. Creating baseline first...");
                        createBaseline(folder);
                        AppLog.info("[+] Baseline created successfully.");
                    }
                    EmailNotifier notifier = EmailNotifier.startDefault();
                    Runtime.getRuntime().addShutdownHook(new Thread(notifier::stop));
                    Monitor.start(folder.toPath());
                    notifier.stop();
                }
                case 4 -> {
                    createBaseline(folder);
                    AppLog.info("\n[+] Baseline updated successfully.");
                }
                default -> AppLog.error("Invalid choice. Please enter 1-4.");
            }
        } catch (Exception e) {
            AppLog.error("Operation failed: " + e.getMessage());
        }
    }

    // ---------- BASELINE FILE (SECURE LOCATION) ----------

    static File getBaselineFile() {

        File dir = new File(System.getProperty("user.home"), ".fim");
        if (!dir.exists()) dir.mkdirs();

        String name = "baseline.db";
        if (rootPath != null && !rootPath.isBlank()) {
            name = "baseline_" + hashString(rootPath) + ".db";
        }
        return new File(dir, name);
    }

    // ---------- BASELINE CREATION ----------

    static void createBaseline(File folder) throws Exception {

        Map<String, FileMeta> map = new HashMap<>();
        scanFolder(folder, map);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(getBaselineFile()))) {
            for (Map.Entry<String, FileMeta> e : map.entrySet()) {
                FileMeta m = e.getValue();
                bw.write(e.getKey() + "|" + m.size + "|" + m.lastModified + "|" + m.hash);
                bw.newLine();
            }
        }
    }

    // ---------- INTEGRITY CHECK ----------

    static void checkIntegrity(File folder) throws Exception {

        Map<String, FileMeta> oldData = loadBaseline();
        Map<String, FileMeta> newData = new HashMap<>();

        scanFolder(folder, newData);

        boolean changesFound = false;

        for (String path : oldData.keySet()) {
            if (!newData.containsKey(path)) {
                FileMeta o = oldData.get(path);
                if (DIR_HASH.equals(o.hash)) {
                    AppLog.info("[DELETED FOLDER] " + path);
                } else {
                    AppLog.info("[DELETED FILE] " + path);
                }
                changesFound = true;
            } else {
                FileMeta o = oldData.get(path);
                FileMeta n = newData.get(path);

                if (DIR_HASH.equals(o.hash)) {
                    if (!DIR_HASH.equals(n.hash)) {
                        AppLog.info("[TYPE CHANGED] " + path);
                        changesFound = true;
                    }
                    continue;
                }

                if (DIR_HASH.equals(n.hash)) {
                    AppLog.info("[TYPE CHANGED] " + path);
                    changesFound = true;
                    continue;
                }

                if (UNREADABLE_HASH.equals(n.hash)) {
                    AppLog.warn("[SKIPPED] " + path + " (unreadable)");
                    changesFound = true;
                    continue;
                }

                if (o.size != n.size ||
                        o.lastModified != n.lastModified ||
                        !o.hash.equals(n.hash)) {

                    AppLog.info("[MODIFIED] " + path);
                    changesFound = true;
                }
            }
        }

        for (String path : newData.keySet()) {
            if (!oldData.containsKey(path)) {
                FileMeta n = newData.get(path);
                if (DIR_HASH.equals(n.hash)) {
                    AppLog.info("[NEW FOLDER] " + path);
                } else {
                    AppLog.info("[NEW FILE] " + path);
                }
                changesFound = true;
            }
        }

        if (!changesFound) {
            AppLog.info("[OK] No changes detected.");
        }

        AppLog.info("\nIntegrity check completed.");
    }

    // ---------- SCAN ----------

    static void scanFolder(File folder, Map<String, FileMeta> map) throws Exception {

        File[] files = folder.listFiles();
        if (files == null) return;

        Path root = Paths.get(rootPath);

        for (File file : files) {

            if (Files.isSymbolicLink(file.toPath())) continue;

            Path filePath;
            try {
                filePath = file.getCanonicalFile().toPath();
            } catch (IOException e) {
                continue;
            }

            if (!filePath.startsWith(root)) continue;

            String relativePath = root.relativize(filePath)
                    .toString()
                    .replace(File.separatorChar, '/');

            if (file.isDirectory()) {
                if (!relativePath.isEmpty()) {
                    map.put(relativePath, new FileMeta(0, 0, DIR_HASH));
                }
                scanFolder(file, map);
                continue;
            }

            long size = file.length();
            long lastModified = file.lastModified();

            FileMeta old = map.get(relativePath);
            if (old != null &&
                    old.size == size &&
                    old.lastModified == lastModified) {
                continue;
            }

            String hash;
            try {
                hash = getFileHash(file);
            } catch (Exception e) {
                hash = UNREADABLE_HASH;
            }

            map.put(relativePath, new FileMeta(size, lastModified, hash));
        }
    }

    // ---------- HASH ----------

    static String getFileHash(File file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ---------- LOAD BASELINE ----------

    static Map<String, FileMeta> loadBaseline() throws Exception {

        File baseline = getBaselineFile();
        if (!baseline.exists()) {
            throw new FileNotFoundException("Baseline not found. Create baseline first.");
        }

        Map<String, FileMeta> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(baseline))) {
            String line;
            while ((line = br.readLine()) != null) {

                String[] p = line.split("\\|");
                if (p.length != 4) continue;

                try {
                    map.put(p[0],
                            new FileMeta(
                                    Long.parseLong(p[1]),
                                    Long.parseLong(p[2]),
                                    p[3]
                            ));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    // ---------- MONITOR COMPATIBILITY ----------

    public static Map<String, String> loadBaselineForMonitor() throws Exception {

        Map<String, FileMeta> metaMap = loadBaseline();
        Map<String, String> simpleMap = new HashMap<>();

        for (Map.Entry<String, FileMeta> e : metaMap.entrySet()) {
            simpleMap.put(e.getKey(), e.getValue().hash);
        }
        return simpleMap;
    }

    // ---------- META CLASS ----------

    static class FileMeta {
        long size;
        long lastModified;
        String hash;

        FileMeta(long s, long lm, String h) {
            size = s;
            lastModified = lm;
            hash = h;
        }
    }
}

