# Real-Time File Integrity Monitor (Java)

A **real-time, event-driven File Integrity Monitoring (FIM)** system built in Java that tracks file system changes using **OS-level events**, **cryptographic hashing**, and **runtime state tracking**.

This project was upgraded from a baseline-only integrity checker to a **rename-aware, production-style FIM**.

---

## 🚀 Key Features

- Real-time monitoring using Java NIO `WatchService`
- Recursive directory monitoring (all subfolders included)
- SHA-256 hashing for integrity verification
- Rename detection (folder renames handled correctly)
- Accurate delete detection (no false deletes on rename/save)
- Debounce handling for ENTRY_MODIFY spam
- Runtime state as ground truth (baseline ≠ filesystem reality)
- Cross-platform (Windows / Linux / macOS)

---

## 🧠 How It Works

1. A **baseline snapshot** is created (used only as a reference).
2. All directories are **recursively registered** using `WatchService`.
3. File system events are processed in real time:
   - CREATE
   - MODIFY
   - DELETE
4. A **runtime state map** tracks the actual filesystem state.
5. **Rename detection** is implemented by correlating DELETE + CREATE
   events within a safe time window.
6. Hashes are recalculated only when required (debounced).
7. Events are reported in a **user-accurate format**.

---

## 📊 Example Output

```text
[NEW FOLDER] docs
[NEW FILE] docs/readme.txt
[MODIFIED] docs/readme.txt
[RENAMED] docs → documents
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

---

## How to Run
```bash
javac *.java
java FIM
```

## Usage

1. Enter the folder path to monitor
2. Choose Create Baseline (one-time reference snapshot)
3. Restart the program  
4. Choose Start Real-Time Monitoring
5. Create, modify, or delete files in the monitored folder to see alerts  
6. Observe real-time integrity events in the console

--- 

## Limitations

- File rename detection with hash continuity is not yet implemented
- Runs as a foreground process  
- Email alerts are not enabled in the current version  
- No persistent event logging yet

---

## Future Enhancements

- File rename detection with hash continuity
- Attack vs legitimate change classification
- Persistent JSON event logs
- GUI-based monitoring dashboard  
- Background service / daemon mode

---

## Author

Nand Gopal Sharma

---
