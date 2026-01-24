# File Integrity Monitor (Java)

A real-time File Integrity Monitoring (FIM) system built in Java that detects unauthorized file changes using cryptographic hashing and OS-level file system events.

## Features
- Real-time file monitoring using Java WatchService
- SHA-256 based file integrity verification
- Detects file creation, modification, and deletion
- Baseline snapshot mechanism
- Command-line interface (CLI)
- Modular design (monitoring, hashing, UI separated)

## How It Works
1. A baseline is created by scanning all files and storing their SHA-256 hashes.
2. The system monitors the directory in real time using OS-level events.
3. On any change, the file hash is recalculated and compared with the baseline.
4. Unauthorized changes are immediately reported.

## Technologies Used
- Java
- Java NIO WatchService
- SHA-256 (MessageDigest)
- File I/O


## How to Run
```bash
javac *.java
java FIM
```

## Usage

1. Enter the folder path to monitor  
2. Select option `1` to create a baseline  
3. Restart the program  
4. Select option `3` to start real-time monitoring  
5. Create, modify, or delete files in the monitored folder to see alerts  

## Limitations

- Subdirectory monitoring is non-recursive  
- Runs as a foreground process  
- Email alerts are not enabled in the current version  

## Future Enhancements

- Recursive directory monitoring  
- Email alert integration  
- Background service support  
- GUI-based monitoring dashboard  

## Author

Nand Gopal Sharma

---
