# 🛡️ Real-Time File Integrity Monitor (FIM)

![Java](https://img.shields.io/badge/Java-17-orange)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)
![License](https://img.shields.io/badge/License-Open_Source-blue)

A robust, real-time **File Integrity Monitoring (FIM)** system engineered in Java. This tool provides deep security visibility into filesystem activities by detecting unauthorized changes—including file creation, modification, deletion, renaming, and moving—using cryptographic verification and OS-level hooks.

---

## 📑 Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture & Technical Specifications](#architecture--technical-specifications)
- [Event Types Tracked](#event-types-tracked)
- [Prerequisites](#prerequisites)
- [Installation & Execution](#installation--execution)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [License & Author](#license--author)

## 📌 Overview
The FIM tool is designed to provide immediate security visibility into critical filesystem events. Unlike traditional polling-based monitors that consume high CPU, this system leverages Java NIO's `WatchService` for event-driven detection. This ensures minimal system overhead while maintaining near-instantaneous alert generation. 

It validates file integrity using **SHA-256** checksum hashing. This allows the system to distinguish between superficial timestamp touch events and actual unauthorized content modifications.

## 🚀 Key Features
- **Cryptographic Verification**: Uses `SHA-256` checksums to verify true file content integrity, eliminating false-positive modify events.
- **Advanced Move & Rename Detection**: Intelligently correlates OS-level `Delete` and `Create` events to accurately identify and differentiate between **File Renames**, **File Moves**, **Folder Renames**, and **Folder Moves**.
- **Event Debouncing Logic**: Intelligent handling of rapid OS events (e.g., during IDE auto-saves or rapid writes) to aggregate actions and prevent alert spam.
- **Real-Time Dashboard**: A specialized fully-featured Swing-based GUI with a live event stream, visual severity indicators, and a dark-mode modern theme.
- **SMTP Notification System**: Integration with Jakarta Mail to securely batch and email consolidated security alerts (with file attachments) to administrators.
- **Daemon Mode**: Built-in CLI headless mode for pure server and production environments.

## ⚙️ Architecture & Technical Specifications

| Component | Specification |
| :--- | :--- |
| **Core Engine** | Java NIO `WatchService` (Event-driven kernel hooks) |
| **Hashing Algorithm** | SHA-256 (256-bit Secure Hash Algorithm) |
| **GUI Framework** | Java Swing (Custom "Modern Dark" Theme) |
| **Concurrency Model** | Producer-Consumer (`BlockingQueue`, `ConcurrentHashMap`) |
| **Notification Pipeline**| SMTPS / SMTP via Jakarta Mail 2.0 |
| **Latency** | < 50ms (Typical event processing time) |

### System Flow
1. **Monitor Engine (`Monitor.java`)**: The core event loop that registers directories with the OS kernel. It gracefully handles `ENTRY_CREATE`, `ENTRY_MODIFY`, and `ENTRY_DELETE` events natively.
2. **State Management**:
    *   **Baseline (`baseline.db`)**: A stored snapshot of the "known good" state mapping files to their secure SHA-256 hashes.
    *   **Runtime State**: A rapid in-memory map tracking the live status of the filesystem to handle concurrent alterations.
3. **Alert Bus**: A decoupled thread-safe publisher-subscriber bus routing isolated events to the GUI and the Email batching engines asynchronously.

## 🔍 Event Types Tracked

The Event Dashboard color-codes and tracks the following anomalies:
- 🟢 **NEW_FILE** / **NEW_FOLDER**: A new item was created.
- 🔴 **DELETED_FILE** / **DELETED_FOLDER**: An item was removed.
- 🟡 **MODIFIED**: A file's SHA-256 hash was altered.
- 🔵 **RESTORED**: A modified file was reverted back to its original baseline hash.
- 🟣 **RENAMED_FILE** / **RENAMED_FOLDER**: An item was renamed within the same parent directory.
- 🟣 **MOVED_FILE** / **MOVED_FOLDER**: An item was moved from one directory to another.

## 💻 Prerequisites
- **Java Development Kit (JDK)**: Version 17 or higher.
- **Operating System**: Windows, macOS, or Linux.
- *(Optional)* **SMTP Server**: For email integrations (e.g., Gmail App Passwords, SendGrid).

## 🛠️ Installation & Execution

### 1. Compile the Project
Navigate to the project root and compile the source code, including the required Jakarta Mail dependencies.
```bash
javac -cp "lib/*" *.java
```

### 2. Run the Graphical Interface (Recommended)
Launch the interactive visual dashboard.
```bash
# Windows
java -cp ".;lib/*" Gui

# Linux / macOS
java -cp ".:lib/*" Gui
```

### 3. Run Command Line Interface (Headless)
For server environments without a display.
```bash
# Windows
java -cp ".;lib/*" FIM

# Linux / macOS
java -cp ".:lib/*" FIM
```

## 🔧 Configuration
The application's Email service is natively configured via **Environment Variables** for deployment flexibility (12-Factor App methodology).

| Variable | Description | Default |
| :--- | :--- | :--- |
| `FIM_SMTP_HOST` | SMTP Server Host Address (e.g., `smtp.gmail.com`) | *Required for Email* |
| `FIM_SMTP_PORT` | SMTP Port (e.g., `587` or `465`) | `25` |
| `FIM_SMTP_USER` | SMTP Username | `""` |
| `FIM_SMTP_PASS` | SMTP Password | `""` |
| `FIM_SMTP_STARTTLS` | Enable STARTTLS for secure connection | `false` |
| `FIM_MAIL_TO` | Recipient email addresses (comma-separated) | *Required for Email* |
| `FIM_BATCH_SEC` | Time window (seconds) to batch alerts before sending | `45` |
| `FIM_ATTACH_MAX_BYTES` | Max size of changed files to attach in emails | `524288` (512KB) |

## 📁 Project Structure
```text
.
├── AlertBus.java            # Thread-safe pub-sub event bus
├── AlertEvent.java          # Core tracking Enum & Dataclass
├── EmailNotifier.java       # Batched email worker thread
├── EmailService.java        # Email configurator
├── EventTableModel.java     # GUI Data model handler
├── FIM.java                 # CLI Entrypoint & Integrity Verification logic
├── Gui.java                 # Main GUI Dashboard 
├── GuiConfig.java           # Configuration mappings 
├── GuiController.java       # Core application controller
├── Monitor.java             # WatchService Native OS Engine
├── MonitorSession.java      # Process session wrapper
├── Theme.java               # Custom UI scaling & color schemes
├── lib/
│   ├── jakarta.activation-2.0.1.jar
│   └── jakarta.mail-2.0.2.jar
└── docs/
    ├── Project_Report.md
    └── System_Flow_Analysis.md
```

## 📜 License
This project is open-source and is available for educational and security auditing purposes.

## 👨‍💻 Author
**Nand Gopal Sharma**