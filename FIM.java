import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class FIM {

    static String rootPath;
    static final String DIR_HASH = "DIR";
    static final String UNREADABLE_HASH = "UNREADABLE";

    // ───────────────── MAIN ─────────────────

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter folder path: ");
        String folderPath = sc.nextLine();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Invalid folder path!");
            return;
        }

        try {
            rootPath = folder.getCanonicalPath();
        } catch (IOException e) {
            System.err.println("Failed to resolve folder path.");
            return;
        }

        System.out.println("\n1. Create Baseline");
        System.out.println("2. Check Integrity");
        System.out.println("3. Start Real-Time Monitoring");
        System.out.println("4. Update Baseline");
        System.out.print("Choose option: ");

        int choice;
        try {
            choice = Integer.parseInt(sc.nextLine());
        } catch (Exception e) {
            System.out.println("Invalid choice!");
            return;
        }

        try {
            switch (choice) {
                case 1 -> {
                    createBaseline(folder);
                    System.out.println("\n[+] Baseline created successfully.");
                }
                case 2 -> checkIntegrity(folder);
                case 3 -> {
                    if (!getBaselineFile().exists()) {
                        System.out.println("[!] Baseline not found. Creating baseline first...");
                        createBaseline(folder);
                        System.out.println("[+] Baseline created successfully.");
                    }
                    EmailNotifier notifier = EmailNotifier.startDefault();
                    Runtime.getRuntime().addShutdownHook(new Thread(notifier::stop));
                    Monitor.start(folder.toPath());
                    notifier.stop();
                }
                case 4 -> {
                    createBaseline(folder);
                    System.out.println("\n[+] Baseline updated successfully.");
                }
                default -> System.out.println("Invalid choice!");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // ───────────────── BASELINE FILE (SECURE LOCATION) ─────────────────

    static File getBaselineFile() {

        File dir = new File(System.getProperty("user.home"), ".fim");
        if (!dir.exists()) dir.mkdirs();

        return new File(dir, "baseline.db");
    }

    // ───────────────── BASELINE CREATION ─────────────────

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

    // ───────────────── INTEGRITY CHECK ─────────────────

    static void checkIntegrity(File folder) throws Exception {

        Map<String, FileMeta> oldData = loadBaseline();
        Map<String, FileMeta> newData = new HashMap<>();

        scanFolder(folder, newData);

        boolean changesFound = false;

        for (String path : oldData.keySet()) {
            if (!newData.containsKey(path)) {
                FileMeta o = oldData.get(path);
                if (DIR_HASH.equals(o.hash)) {
                    System.out.println("[DELETED FOLDER] " + path);
                } else {
                    System.out.println("[DELETED FILE] " + path);
                }
                changesFound = true;
            } else {
                FileMeta o = oldData.get(path);
                FileMeta n = newData.get(path);

                if (DIR_HASH.equals(o.hash)) {
                    if (!DIR_HASH.equals(n.hash)) {
                        System.out.println("[TYPE CHANGED] " + path);
                        changesFound = true;
                    }
                    continue;
                }

                if (DIR_HASH.equals(n.hash)) {
                    System.out.println("[TYPE CHANGED] " + path);
                    changesFound = true;
                    continue;
                }

                if (UNREADABLE_HASH.equals(n.hash)) {
                    System.out.println("[SKIPPED] " + path + " (unreadable)");
                    changesFound = true;
                    continue;
                }

                if (o.size != n.size ||
                        o.lastModified != n.lastModified ||
                        !o.hash.equals(n.hash)) {

                    System.out.println("[MODIFIED] " + path);
                    changesFound = true;
                }
            }
        }

        for (String path : newData.keySet()) {
            if (!oldData.containsKey(path)) {
                FileMeta n = newData.get(path);
                if (DIR_HASH.equals(n.hash)) {
                    System.out.println("[NEW FOLDER] " + path);
                } else {
                    System.out.println("[NEW FILE] " + path);
                }
                changesFound = true;
            }
        }

        if (!changesFound) {
            System.out.println("[OK] No changes detected.");
        }

        System.out.println("\nIntegrity check completed.");
    }

    // ───────────────── SCAN ─────────────────

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

    // ───────────────── HASH ─────────────────

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

    // ───────────────── LOAD BASELINE ─────────────────

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

    // ───────────────── MONITOR COMPATIBILITY ─────────────────

    public static Map<String, String> loadBaselineForMonitor() throws Exception {

        Map<String, FileMeta> metaMap = loadBaseline();
        Map<String, String> simpleMap = new HashMap<>();

        for (Map.Entry<String, FileMeta> e : metaMap.entrySet()) {
            simpleMap.put(e.getKey(), e.getValue().hash);
        }
        return simpleMap;
    }

    // ───────────────── META CLASS ─────────────────

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

