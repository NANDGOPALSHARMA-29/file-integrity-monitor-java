# Real-Time File Integrity Monitor (Java)

A **real-time, event-driven File Integrity Monitoring (FIM)** system built in Java that tracks filesystem changes using **OS-level events**, **cryptographic hashing**, and a **runtime state map**.

This project evolved from a baseline-only checker into a **rename-aware, real-time FIM**.

---

## Key Features

- Real-time monitoring using Java NIO `WatchService`
- Recursive directory monitoring (all subfolders included)
- SHA-256 hashing for integrity verification
- Rename and move detection (files and folders)
- Accurate delete detection (no false deletes on rename/save)
- Debounce handling for ENTRY_MODIFY event spam
- Runtime state as ground truth (baseline != live filesystem)
- Cross-platform (Windows / Linux / macOS)

---

## How It Works (FIM.java + Monitor.java + EmailNotifier.java)

### FIM.java (CLI controller)

1. Takes **folder path** input and validates it.
2. Provides menu options:
   - Create Baseline
   - Check Integrity
   - Start Real-Time Monitoring
   - Update Baseline
3. Baseline creation:
   - Recursively scans files/folders.
   - Saves `{relativePath | size | lastModified | hash}` to `~/.fim/baseline.db`.
4. Integrity check:
   - Loads baseline, rescans folder, compares:
     - `[NEW]`, `[MODIFIED]`, `[DELETED]`, `[TYPE CHANGED]`.
5. Real-time monitoring:
   - Ensures baseline exists, then calls `Monitor.start(...)`.
   - Starts `EmailNotifier` (SMTP if configured, console fallback otherwise).

### Monitor.java (real-time engine)

1. Loads baseline into:
   - `baselineDisk` (reference)
   - `runtimeState` (ground truth)
2. Registers **all directories** with `WatchService`.
3. Main loop:
   - Polls events.
   - Routes each event to `handleEvent(...)`.
4. Handles logic:
   - **Debounce** noisy modify events.
   - **Delete delay** to confirm real deletes vs renames.
   - **Rename/move detection** using hash + time window.
   - **Folder rename detection** using parent + time window.
5. `cleanupDeletes(...)` finalizes deletes and clears stale renames.

---

## System Diagram (Data + Control Flow)

```text
User Input (Folder Path)
        |
        v
FIM.java (menu + control)
        |
        +--> Create Baseline
        |       |
        |       v
        |   scanFolder -> hash/size/time -> ~/.fim/baseline.db
        |
        +--> Check Integrity
        |       |
        |       v
        |   loadBaseline + scanFolder
        |       |
        |       v
        |   compare -> [NEW / MODIFIED / DELETED / TYPE CHANGED]
        |
        +--> Start Monitoring
                |
                v
        Monitor.start(rootDir)
                |
                +--> loadBaselineForMonitor
                |       |
                |       v
                |   baselineDisk + runtimeState
                |
                +--> registerAll(rootDir)
                |
                +--> Watch Loop
                        |
                        v
                 handleEvent(...)
                        |
        +---------------+----------------+
        |               |                |
        v               v                v
     NEW FILE        MODIFIED          DELETED
     RESTORED        RENAMED           MOVED
```

---

## Example Output

```text
[NEW FOLDER] docs
[NEW FILE] docs/readme.txt
[MODIFIED] docs/readme.txt
[RENAMED] docs -> documents
[DELETED FILE] documents/readme.txt
```
---

## Technologies Used
- Java
- Java NIO (WatchService, Path, Files)
- SHA-256 (MessageDigest)
- File I/O
- Concurrent collections (ConcurrentHashMap)
- OS-level filesystem events
- Jakarta Mail + Activation (SMTP email notifications + attachments)

---

## How to Run
```bash
javac -cp "lib/*" *.java
java -cp ".;lib/*" FIM
```

## Usage

1. Enter the folder path to monitor
2. Choose Create Baseline (one-time reference snapshot)
3. Restart the program
4. Choose Start Real-Time Monitoring
5. Create, modify, or delete files in the monitored folder to see alerts
6. Observe real-time integrity events in the console (and email if configured)

---

## Email Configuration (Optional)

Email sending is enabled when SMTP environment variables are set. If `FIM_SMTP_HOST` is empty, emails are printed to the console instead.

Required for SMTP:
- `FIM_SMTP_HOST`
- `FIM_MAIL_TO` (comma-separated)

Common optional settings:
- `FIM_SMTP_PORT` (default: `25`)
- `FIM_SMTP_STARTTLS` (default: `false`)
- `FIM_SMTP_USER`
- `FIM_SMTP_PASS`
- `FIM_MAIL_FROM` (default: `fim@localhost`)
- `FIM_MAIL_SUBJECT` (default: `[FIM]`)
- `FIM_BATCH_SEC` (default: `45`)
- `FIM_ATTACH_MAX_BYTES` (default: `524288`)

---

## Limitations

- Runs as a foreground process
- Email alerts require SMTP configuration (see above)
- No persistent event logging yet

---

## Future Enhancements

- Attack vs legitimate change classification
- Persistent JSON event logs
- GUI-based monitoring dashboard
- Background service / daemon mode

---

## Author

Nand Gopal Sharma
