# REAL-TIME FILE INTEGRITY MONITOR

A robust, real-time File Integrity Monitoring system engineered in Java. This tool detects unauthorized changes to the filesystem including file creation, modification, deletion, and renaming events using cryptographic verification and OS-level hooks.

## � Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation & Execution](#installation--execution)
- [Configuration](#configuration)
- [Project Structure](#project-structure)

## Overview
The FIM tool is designed to provide security visibility into filesystem activities. Unlike traditional polling-based monitors, this system leverages Java NIO's `WatchService` for event-driven detection, ensuring minimal CPU overhead while maintaining near-instantaneous alert generation. It validates file integrity using **SHA-256** hashing to distinguish between superficial timestamp changes and actual content modification.

## Architecture
The application operates on a multithreaded architecture:
1.  **Monitor Engine (`Monitor.java`)**: The core event loop that registers directories with the OS kernel. It handles `ENTRY_CREATE`, `ENTRY_MODIFY`, and `ENTRY_DELETE` events.
2.  **State Management**:
    *   **Baseline**: A persistent snapshot of the "known good" state.
    *   **Runtime State**: An in-memory map tracking the live status of the filesystem to detect anomalies like "Renames" (which the OS often reports as separate Delete/Create events).
3.  **Alert Bus**: A decoupled publisher-subscriber system that routes events to the GUI and Email Notification services asynchronously.

## Features
*   **Cryptographic Verification**: Uses `SHA-256` checksums to verify file content integrity.
*   **Smart Rename Detection**: Correlates Delete and Create events to identify file moves and renames.
*   **Deboucing Logic**: Intelligent handling of rapid OS events (e.g., during file saves) to prevent false positives.
*   **Real-Time Dashboard**: A specialized Swing-based GUI with a live event stream and visual severity indicators.
*   **Notification System**: Integration with SMTP to send consolidated alert batches to administrators.

## Prerequisites
*   **Java Development Kit (JDK)**: Version 17 or higher.
*   **Operating System**: Windows, macOS, or Linux.

## Installation & Execution

### 1. Compile the Project
Navigate to the project root and compile the source code, including dependencies.
```bash
javac -cp "lib/*" *.java
```

### 2. Run the Graphical Interface (Recommended)
Launch the dashboard for interactive monitoring.
```bash
# Windows
java -cp ".;lib/*" Gui

# Linux / macOS
java -cp ".:lib/*" Gui
```

### 3. Run Command Line Interface (Headless)
For server environments without a display.
```bash
java -cp ".;lib/*" FIM
```

## Configuration
The application can be configured via Environment Variables for deployment flexibility.

| Variable | Description | Default |
| :--- | :--- | :--- |
| `FIM_SMTP_HOST` | SMTP Server Address (e.g., `smtp.gmail.com`) | *Required for Email* |
| `FIM_SMTP_PORT` | SMTP Port | `25` |
| `FIM_MAIL_TO` | Recipient email addresses (comma-separated) | *Required for Email* |
| `FIM_BATCH_SEC` | Time window (seconds) to batch alerts before sending | `45` |
| `FIM_ATTACH_MAX_BYTES` | Max size of changed files to attach in emails | `524288` (512KB) |

## Project Structure
```text
src/
├── Monitor.java       # Core event loop and logic
├── FIM.java           # CLI Entry point and Baseline management
├── Gui.java           # Main GUI Entry point
├── GuiController.java # Logic separating View and Model
├── AlertBus.java      # Event pub/sub system
├── AlertEvent.java    # Data carrier for events
├── EmailNotifier.java # SMTP handling logic
└── Theme.java         # UI Look and Feel definitions
```

## License
This project is open-source and available for educational and security auditing purposes.

## Author
**NAND GOPAL SHARMA**
