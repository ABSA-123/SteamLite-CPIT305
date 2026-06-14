# Steam Lite

Steam Lite is a CPIT-305 Advanced Programming final project. It is a small multi-user game library platform inspired by Steam, built in Java to demonstrate networking, multithreading, file I/O streams, and database programming with JDBC.

The application uses a client-server architecture. A TCP server manages users, the game catalog, wallet balances, and downloads, while an interactive command-line client lets users register, log in, browse games, buy games, download game files, and check their balance.

## Features

- Multi-threaded TCP server that supports multiple clients at the same time.
- Interactive command-line client.
- User registration and login with SHA-256 password hashing.
- SQLite database for accounts, games, balances, and download history.
- Game catalog loaded from text files in `resources/games`.
- Simulated game asset files stored as `.bin` files.
- Purchase and download workflow with balance deduction.
- Re-download support for already purchased games without charging again.
- Chunked file transfer from server to client with progress display.
- Thread-safe logging to `steamlite.log`.
- Custom test runner for core database and utility tests.

## Course Concepts Demonstrated

| Concept | Implementation |
| --- | --- |
| IO Streams | Reads game metadata with `BufferedReader`, creates binary assets with `DataOutputStream`, streams downloads with buffered file streams, and writes logs to a file. |
| Multithreading | Uses `ExecutorService` with a fixed thread pool to handle concurrent client sessions. |
| Networking | Uses Java `ServerSocket` and `Socket` with serialized `Message` objects over object streams. |
| Database | Uses SQLite through JDBC with tables for games, accounts, and downloads. |

## Tech Stack

- Java 21
- Maven
- SQLite JDBC
- SLF4J Simple

## Prerequisites

- Java 21 or newer
- Maven 3.8 or newer

## Build

From the `SteamLite` folder, run:

```bash
mvn clean package
```

This creates:

- `target/SteamLite-Server.jar`
- `target/SteamLite-Client.jar`
- `target/SteamLite-Tests.jar`

## Run the Server

```bash
java -jar target/SteamLite-Server.jar
```

The server starts on port `9090`, creates or opens `steamlite.db`, creates the database tables, and loads the game catalog from `resources/games`.

## Run the Client

Open another terminal and run:

```bash
java -jar target/SteamLite-Client.jar
```

To connect to a different host or port:

```bash
java -jar target/SteamLite-Client.jar <host> <port>
```

## Client Commands

| Command | Description |
| --- | --- |
| `register <user> <pass> <dob>` | Create a new account. |
| `login <user> <pass>` | Log in to an existing account. |
| `logout` | Log out of the current account. |
| `list` | Display the available game catalog. |
| `detail <id>` | Show details for one game. |
| `download <id>` | Buy and download a game. |
| `balance` | Show the current wallet balance. |
| `help` | Show the command menu. |
| `quit` | Exit the client. |

## Run Tests

```bash
java -jar target/SteamLite-Tests.jar
```

The tests cover password hashing, account registration and login, game retrieval, balance deduction, and download history.

## Notes

- The server must be running before starting the client.
- Multiple client terminals can connect to the same server.
- Downloaded files are saved in the client-side `downloads` folder.
- New users start with a default wallet balance of `$100.00`.
- Re-downloading an already owned game does not deduct the balance again.

## Course

CPIT-305 Advanced Programming  
King Abdulaziz University  
Faculty of Computing and Information Technology
