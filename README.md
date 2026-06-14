# Steam Lite

Steam Lite is a small multi-user game library platform inspired by Steam, built in Java to demonstrate networking, multithreading, file I/O streams, and database programming with JDBC.

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

## Project Structure

```text
SteamLite/
+-- pom.xml
+-- README.md
+-- resources/
|   +-- games/
|       +-- game1.txt
|       +-- game1.bin
|       +-- ...
+-- downloads/
+-- src/main/java/steamlite/
|   +-- client/
|   |   +-- GameClient.java
|   +-- db/
|   |   +-- DatabaseManager.java
|   +-- model/
|   |   +-- Game.java
|   |   +-- Message.java
|   |   +-- UserAccount.java
|   +-- server/
|   |   +-- ClientHandler.java
|   |   +-- GameServer.java
|   +-- tests/
|   |   +-- SteamLiteTests.java
|   +-- util/
|       +-- GameLoader.java
|       +-- Logger.java
|       +-- PasswordUtil.java
+-- target/
    +-- SteamLite-Server.jar
    +-- SteamLite-Client.jar
    +-- SteamLite-Tests.jar
```

## Prerequisites

- Java 21 or newer
- Maven 3.8 or newer

## Build

From the `SteamLite` folder, run:

```bash
mvn clean package
```

The Maven build creates three runnable JAR files:

- `target/SteamLite-Server.jar`
- `target/SteamLite-Client.jar`
- `target/SteamLite-Tests.jar`

## Run the Server

Start the server first:

```bash
java -jar target/SteamLite-Server.jar
```

The server starts on port `9090`. On startup, it creates or opens `steamlite.db`, creates the required database tables, and loads the game catalog from `resources/games`.

## Run the Client

Open another terminal and run:

```bash
java -jar target/SteamLite-Client.jar
```

By default, the client connects to:

```text
localhost:9090
```

To connect to a different host or port:

```bash
java -jar target/SteamLite-Client.jar <host> <port>
```

Example:

```bash
java -jar target/SteamLite-Client.jar localhost 9090
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

## Example Usage

```text
steamlite> register alice pass123 2000-01-01
Account created for alice. You may now log in.

steamlite> login alice pass123
Welcome back, alice! Balance: $100.00

steamlite> list
=== Game Catalog ===
[1] Shadow Realms | Phantom Studios | RPG | $29.99

steamlite> detail 1
[1] Shadow Realms | Phantom Studios | RPG | $29.99

steamlite> download 1
Downloading: Shadow Realms
[####################] 100%
Download complete.

steamlite> balance
Balance: $70.01
```

## Run Tests

After building the project, run:

```bash
java -jar target/SteamLite-Tests.jar
```

The tests cover:

- Password hashing
- Account registration and login
- Game insertion and retrieval
- Balance deduction
- Download recording and download counts

## Database

Steam Lite uses a local SQLite database named:

```text
steamlite.db
```

The database contains three main tables:

- `Games`: stores game catalog information.
- `Accounts`: stores user accounts, password hashes, dates of birth, and wallet balances.
- `Downloads`: stores which users downloaded which games and how many times.

The schema is created automatically when the server starts.

## Game Resource Files

Game metadata is stored in text files under:

```text
resources/games
```

Each metadata file uses this format:

```text
title=Shadow Realms
developer=Phantom Studios
genre=RPG
price=29.99
description=An epic open-world RPG set in a dark fantasy universe.
```

Each game also has a matching `.bin` file that represents the downloadable game asset. If the asset file is missing, the project can create a simulated binary asset automatically.

## Notes

- The server must be running before starting the client.
- Multiple client terminals can connect to the same server.
- Downloaded files are saved in the client-side `downloads` folder.
- New users start with a default wallet balance of `$100.00`.
- Re-downloading an already owned game does not deduct the balance again.

