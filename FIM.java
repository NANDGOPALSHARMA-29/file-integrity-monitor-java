import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class FIM {

    static String BASELINE_FILE = "baseline.txt";
    static String rootPath;

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        System.out.print("Enter folder path: ");
        String folderPath = sc.nextLine();

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Invalid folder path!");
            return;
        }

        rootPath = folder.getCanonicalPath();

        System.out.println("\n1. Create Baseline");
        System.out.println("2. Check Integrity");
        System.out.println("3. Start Real-Time Monitoring");
        System.out.print("Choose option: ");

        int choice = sc.nextInt();

        if (choice == 1) {
            createBaseline(folder);
            System.out.println("\n[+] Baseline created successfully.");
        }
        else if (choice == 2) {
            checkIntegrity(folder);
        }
        else if (choice == 3) {
            Monitor.start(folder.toPath(), rootPath);
        }
        else {
            System.out.println("Invalid choice!");
        }
    }

    // ---------------- BASELINE ----------------
    static void createBaseline(File folder) throws Exception {
        Map<String, String> fileHashes = new HashMap<>();
        scanFolder(folder, fileHashes);

        BufferedWriter bw = new BufferedWriter(new FileWriter(BASELINE_FILE));
        for (String path : fileHashes.keySet()) {
            bw.write(path + "|" + fileHashes.get(path));
            bw.newLine();
        }
        bw.close();
    }

    // ---------------- CHECK ----------------
    static void checkIntegrity(File folder) throws Exception {
        Map<String, String> oldHashes = loadBaseline();
        Map<String, String> newHashes = new HashMap<>();

        scanFolder(folder, newHashes);

        boolean changesFound = false;

        for (String path : oldHashes.keySet()) {
            if (!newHashes.containsKey(path)) {
                System.out.println("[DELETED] " + path);
                changesFound = true;
            } else if (!oldHashes.get(path).equals(newHashes.get(path))) {
                System.out.println("[MODIFIED] " + path);
                changesFound = true;
            }
        }

        for (String path : newHashes.keySet()) {
            if (!oldHashes.containsKey(path)) {
                System.out.println("[NEW FILE] " + path);
                changesFound = true;
            }
        }

        if (!changesFound) {
            System.out.println("[OK] No changes detected.");
        }

        System.out.println("\nIntegrity check completed.");
    }

    // ---------------- UTILS ----------------
    static void scanFolder(File folder, Map<String, String> map) throws Exception {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                scanFolder(file, map);
            } else {
                String fullPath = file.getCanonicalPath();
                String relativePath = fullPath.substring(rootPath.length());
                map.put(relativePath, getFileHash(file));
            }
        }
    }

    public static String getFileHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        fis.close();

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static Map<String, String> loadBaseline() throws Exception {
        Map<String, String> map = new HashMap<>();
        File file = new File(BASELINE_FILE);

        if (!file.exists()) {
            System.out.println("Baseline not found! Create baseline first.");
            System.exit(0);
        }

        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\\|");
            map.put(parts[0], parts[1]);
        }
        br.close();

        return map;
    }
}
