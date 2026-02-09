# System Flow Analysis & Application Architecture

This document provides a comprehensive breakdown of the File Integrity Monitor (FIM) system, detaling the role of every file, the lifecycle of an event, and the execution flows for both CLI and GUI modes.

## 1. File Role Breakdown (Sari Files Ka Kya Kaam Hai)

| File Name | Component Type | Responsibility / Description |
| :--- | :--- | :--- |
| **`FIM.java`** | **Core / CLI** | **The Brain.** Contains the `main` method for CLI. Handles core logic like generating file hashes (`SHA-256`), checking integrity against baseline, and creating the baseline database. |
| **`Monitor.java`** | **Engine** | **The Eyes.** Runs the infinite loop that watches the filesystem using Java NIO `WatchService`. It detects raw OS events, filters noise, detects "Renames" (by correlating delete+create), and emits `AlertEvent`s. |
| **`Gui.java`** | **Frontend** | **The Face.** The entry point for the Graphical User Interface. Builds the Swing window, buttons, and the real-time event table. Delegates user actions to the `GuiController`. |
| **`GuiController.java`** | **Controller** | **The Bridge.** Connects the GUI (View) to the Backend logic (`FIM`/`Monitor`). It handles button clicks (Start/Stop), manages the background monitor thread, and updates the UI when new events arrive. |
| **`AlertBus.java`** | **Communication** | **The Pipes.** A thread-safe messaging hub. `Monitor` publishes events here. The GUI subscribes to show them instantly, and `EmailNotifier` pulls them to send alerts. |
| **`EmailNotifier.java`** | **Service** | **The Messenger.** Runs in the background. It waits for events from `AlertBus`, collects them into a "Batch" (e.g., every 45 secs), and sends a consolidated email using SMTP. |
| **`EventTableModel.java`** | **UI Model** | **The Data Holder.** Stores the list of events shown in the GUI table. Updates the table view when new events are added. |
| **`Theme.java`** | **UI Styling** | **The Style.** Defines the "Dark Mode" colors (Backgrounds, Fonts, Status colors like Red for Delete, Green for New) used by the GUI. |
| **`ModernButton.java`** | **UI Component** | **The Look.** A custom Swing button class that looks modern (flat design, hover effects) instead of the default old Java buttons. |
| **`GuiConfig.java`** | **Config** | **The Settings.** Manages configuration for the GUI, such as Email Enabled/Disabled status, Batch time, and limits. |
| **`MonitorSession.java`** | **Helper** | **The Wrapper.** A small utility to manage the lifecycle (Start/Stop) of the `Monitor` thread safely without freezing the GUI. |
| **`AlertEvent.java`** | **Data Object** | **The Message.** A simple class (POJO) representing a single detected change (Type, Path, Timestamp). |
| **`AppLog.java`** | **Logging** | **The Recorder.** Centralized logging utility. Prints to console and can redirect errors to the GUI if needed. |
| **`EmailService.java`** | **Wrapper** | **The Manager.** Manages the `EmailNotifier` thread, allowing it to be paused/resumed or reconfigured on the fly from the GUI. |
| **`baseline.txt`** | **Data Store** | **The Memory.** (Generated file) Stores the "Known Good" state of files (Path | Size | ModifiedTime | Hash). |

---

## 2. Event Propagation Flow (Kis Event Me Kon Si File Use Ho Rhi Hai)

This flow describes what happens technically when a user modifies a file while the system is running.

### Scenario: User modifies `secret.txt`
1.  **OS Kernel**: Detects the file change and notifies the JVM.
2.  **`Monitor.java`**:
    *   Wakes up inside its `while(running)` loop.
    *   Receives `ENTRY_MODIFY` event.
    *   **Debouncing**: Puts the event in `pendingModifies` map to wait a few milliseconds (to ensure the file save is complete).
    *   **Processing**: Calculates the new SHA-256 hash.
    *   **Verification**: Compares the new hash with the `runtimeState`. If different, it confirms a modification.
    *   **Action**: Calls `emitEvent(AlertEvent.Type.MODIFIED, ...)`.
3.  **`Monitor.java`** -> calls -> **`AlertBus.publish(event)`**.
4.  **`AlertBus.java`**:
    *   **Path A (GUI)**: Immediately loops through registered listeners (i.e., `GuiController`).
    *   **Path B (Email)**: Pushes the event into a thread-safe `Queue`.

### Parallel Consumption:
*   **Path A (GUI Updates)**:
    *   `AlertBus` calls `GuiController` listener.
    *   `GuiController` calls `view.addEvent(event)`.
    *   `Gui.java` (inside `addEvent`) uses `SwingUtilities.invokeLater` to ensure thread safety.
    *   `EventTableModel` adds the row to the list and fires `fireTableRowsInserted`.
    *   **Result**: The user sees the new row appear instantly on the screen.

*   **Path B (Email Notification)**:
    *   `EmailNotifier.java` (running in a separate thread) wakes up because `Queue` is no longer empty.
    *   It starts a timer (Batch Window, e.g., 45s).
    *   It collects all other events that happen during this window.
    *   Once the timer expires, it formats an email body.
    *   It uses `Jakarta Mail` APIs to connect to the SMTP server.
    *   **Result**: The admin receives one email summarizing all changes.

---

## 3. Execution Flows (CLI vs GUI)

### A. CLI Flow (Command Line Interface)
**Entry Point**: `FIM.main(args)`

1.  **Start**: User runs `java FIM`.
2.  **Input**: `FIM.java` uses `Scanner` to ask for the "Folder Path".
3.  **Menu**: Displays options (1. Create Baseline, 2. Integrity Check, 3. Real-time Monitor).
4.  **Action**:
    *   **If Option 1 (Baseline)**: Calls `FIM.createBaseline()`. Scans all files, hashes them, writes to `baseline.db`.
    *   **If Option 2 (Check)**: Calls `FIM.checkIntegrity()`. Scans files, compares with `baseline.db`, prints differences to Console.
    *   **If Option 3 (Monitor)**:
        *   Checks if baseline exists (creates if missing).
        *   Starts `EmailNotifier` thread.
        *   Calls `Monitor.start(path)`. This **blocks** the main thread and runs forever until stopped (Ctrl+C).

### B. GUI Flow (Graphical User Interface)
**Entry Point**: `Gui.main(args)`

1.  **Start**: User runs `java Gui`.
2.  **Setup**:
    *   `Theme.applyDarkTheme()` sets up the look and feel.
    *   `Gui.buildUi()` constructs the Window, `JTable`, and Buttons.
    *   `GuiController` is initialized.
3.  **Idle State**: App waits for user interaction.
4.  **User Clicks "Start Monitoring"**:
    *   `Gui` calls `controller.startMonitoring(path)`.
    *   `GuiController` validates the folder.
    *   **Background Thread**: `GuiController` spawns a new Thread (`fim-gui-monitor`).
    *   **Inside Thread**:
        *   Checks/Creates Baseline using `FIM` logic.
        *   Starts `Monitor.start(path)` in this background thread (so the UI doesn't freeze).
5.  **Running State**:
    *   `Monitor` is running in the background.
    *   Events flow via `AlertBus` to the GUI Table.
6.  **User Clicks "Stop Monitoring"**:
    *   `Gui` calls `controller.stopMonitoring()`.
    *   `GuiController` calls `MonitorSession.stopAndWait()`.
    *   `Monitor.running` flag is set to `false`.
    *   The background thread exits.
