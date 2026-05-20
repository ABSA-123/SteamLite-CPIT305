# Steam Lite 🎮
### CPIT-305 Advanced Programming – Final Project

A multi-user game library platform (inspired by Steam) built with Java, demonstrating all four core course topics:

| Topic | Implementation |
|---|---|
| **IO Streams** | Game catalog loaded from text files; asset transfer via `FileInputStream`/`BufferedOutputStream`; transaction logging to `steamlite.log` |
| **Multithreading** | Fixed thread pool (`ExecutorService`) handles concurrent clients; `AtomicInteger` for thread-safe client IDs; `synchronized` DB methods |
| **Networking** | TCP `ServerSocket` / `Socket`; serialized `Message` objects over `ObjectOutputStream`/`ObjectInputStream` |
| **Database (JDBC)** | SQLite via JDBC; auto-schema creation; full CRUD for Games, Accounts, Downloads; transactional balance deduction |

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     GameServer (Port 9090)               │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  ServerSocket.accept() loop                         │ │
│  │         │                                           │ │
│  │         ▼                                           │ │
│  │  ExecutorService (10 threads)                       │ │
│  │    ├── ClientHandler #1 ──► Client A                │ │
│  │    ├── ClientHandler #2 ──► Client B                │ │
│  │    └── ClientHandler #N ──► Client ...              │ │
│  └─────────────────────────────────────────────────────┘ │
│                         │                                 │
│                   DatabaseManager                         │
│                  (synchronized JDBC)                      │
│                         │                                 │
│                    steamlite.db (SQLite)                   │
└──────────────────────────────────────────────────────────┘
```

---

## Database Schema

```sql
CREATE TABLE Games (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT NOT NULL UNIQUE,
    developer   TEXT NOT NULL,
    genre       TEXT NOT NULL,
    price       REAL NOT NULL,
    description TEXT,
    file_path   TEXT NOT NULL
);

CREATE TABLE Accounts (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,   -- SHA-256 hashed
    date_of_birth TEXT NOT NULL,
    balance       REAL NOT NULL DEFAULT 100.0
);

CREATE TABLE Downloads (
    account_id      INTEGER NOT NULL,
    game_id         INTEGER NOT NULL,
    download_count  INTEGER NOT NULL DEFAULT 0,
    last_downloaded TEXT,
    PRIMARY KEY (account_id, game_id),
    FOREIGN KEY (account_id) REFERENCES Accounts(id),
    FOREIGN KEY (game_id)    REFERENCES Games(id)
);
```

---

## Project Structure

```
SteamLite/
├── pom.xml                          # Maven build (Java 21, SQLite JDBC)
├── README.md
├── resources/
│   └── games/
│       ├── game1.txt                # Game metadata files (IO Streams)
│       ├── game1.bin                # Game asset files (auto-generated)
│       └── ...
└── src/main/java/steamlite/
    ├── server/
    │   ├── GameServer.java          # TCP server + thread pool
    │   └── ClientHandler.java       # Per-client session handler
    ├── client/
    │   └── GameClient.java          # Interactive CLI client
    ├── db/
    │   └── DatabaseManager.java     # JDBC + all SQL operations
    ├── model/
    │   ├── Game.java                # Game entity
    │   ├── UserAccount.java         # User entity
    │   └── Message.java             # Serializable protocol message
    ├── util/
    │   ├── Logger.java              # Thread-safe dual-output logger
    │   ├── PasswordUtil.java        # SHA-256 password hashing
    │   └── GameLoader.java          # File → DB seeding (IO Streams)
    └── tests/
        └── SteamLiteTests.java      # Unit tests (no external framework)
```

---

## How to Build & Run

### Prerequisites
- Java 21+
- Maven 3.8+

### Step 1 — Build
```bash
cd SteamLite
mvn clean package -q
```
This produces three JARs in `target/`:
- `SteamLite-Server.jar`
- `SteamLite-Client.jar`
- `SteamLite-Tests.jar`

### Step 2 — Run the Server
Open a terminal and run:
```bash
java -jar target/SteamLite-Server.jar
```
The server will:
1. Connect to/create `steamlite.db`
2. Seed game catalog from `resources/games/*.txt`
3. Start listening on port **9090**

### Step 3 — Run the Client
Open one or more additional terminals and run:
```bash
java -jar target/SteamLite-Client.jar
```
To connect to a remote server:
```bash
java -jar target/SteamLite-Client.jar <host> <port>
```

### Step 4 — Run Unit Tests
```bash
java -jar target/SteamLite-Tests.jar
```

---

## Client Commands

| Command | Description |
|---|---|
| `register <user> <pass> <dob>` | Create a new account |
| `login <user> <pass>` | Log in to your account |
| `logout` | Log out |
| `list` | Browse the game catalog |
| `detail <id>` | View a game's details and your download count |
| `download <id>` | Purchase and download a game |
| `balance` | Check your wallet balance |
| `help` | Show command reference |
| `quit` | Exit the client |

---

## Demo Walkthrough

```
steamlite> register alice pass123 2000-05-15
✔ Account created for alice. You may now log in.

steamlite> login alice pass123
✔ Welcome back, alice! Balance: $100.00

steamlite> list
✔ === Game Catalog ===
[1] Shadow Realms  |  Phantom Studios  |  RPG  |  $29.99
    An epic open-world RPG set in a dark fantasy universe.
...

steamlite> download 1
 Downloading: Shadow Realms (10285 bytes)
 [████████████████████] 100%
✔ Download of 'Shadow Realms' complete. Charged: $29.99

steamlite> balance
✔ Balance: $70.01

steamlite> download 1
 Downloading: Shadow Realms (10285 bytes)
 [████████████████████] 100%
✔ Download of 'Shadow Realms' complete. (Already owned - no charge)
```

---

## Technical Highlights

### Multithreading
- `ExecutorService` with a fixed pool of 10 threads prevents resource exhaustion
- `AtomicInteger` for client ID generation (lock-free thread safety)
- All `DatabaseManager` public methods are `synchronized` to prevent race conditions on the SQLite connection
- Graceful shutdown via `awaitTermination()` lets active downloads finish

### IO Streams
- `BufferedReader` reads game metadata text files line-by-line
- `DataOutputStream` writes structured binary game asset files
- `FileInputStream` + `BufferedInputStream` streams game assets in 4KB chunks
- `FileOutputStream` on the client side writes received chunks to disk
- `PrintWriter` (auto-flush) logs all events to `steamlite.log`

### Networking
- `ServerSocket` / `Socket` TCP communication
- `ObjectOutputStream` / `ObjectInputStream` for type-safe `Message` serialization
- `out.reset()` called after each send to prevent Java's object caching from causing stale data
- `ObjectOutputStream` is constructed before `ObjectInputStream` on both ends to avoid deadlock

### Database
- WAL mode + foreign keys enabled on startup
- `INSERT OR IGNORE` for idempotent game seeding
- `INSERT ... ON CONFLICT DO UPDATE` for atomic download count increments
- Transactional `deductBalance` with rollback on insufficient funds

---

## Team Members
- Member 1: [Name] – [Student ID]
- Member 2: [Name] – [Student ID]

## Course
CPIT-305 Advanced Programming  
King Abdulaziz University – Faculty of Computing and Information Technology
