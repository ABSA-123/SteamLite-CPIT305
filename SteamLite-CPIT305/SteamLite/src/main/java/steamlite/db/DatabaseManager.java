package steamlite.db;

import steamlite.model.Game;
import steamlite.model.UserAccount;
import steamlite.util.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages all database operations using JDBC with SQLite.
 * Handles schema creation, user accounts, game catalog, and download records.
 */
public class DatabaseManager implements AutoCloseable {

    private static final String DB_URL = "jdbc:sqlite:steamlite.db";
    private Connection connection;

    public DatabaseManager() throws SQLException {
        connect();
        createSchema();
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    private void connect() throws SQLException {
        // Explicitly load the SQLite JDBC driver (required in some environments)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
        connection = DriverManager.getConnection(DB_URL);
        connection.setAutoCommit(true);
        // Enable WAL mode for concurrent reads
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
        }
        Logger.info("DatabaseManager", "Connected to SQLite database.");
    }

    // ─── Schema ───────────────────────────────────────────────────────────────

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Games table
            st.execute("""
                CREATE TABLE IF NOT EXISTS Games (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    title       TEXT    NOT NULL UNIQUE,
                    developer   TEXT    NOT NULL,
                    genre       TEXT    NOT NULL,
                    price       REAL    NOT NULL,
                    description TEXT,
                    file_path   TEXT    NOT NULL
                )
            """);

            // Accounts table
            st.execute("""
                CREATE TABLE IF NOT EXISTS Accounts (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT    NOT NULL UNIQUE,
                    password_hash TEXT    NOT NULL,
                    date_of_birth TEXT    NOT NULL,
                    balance       REAL    NOT NULL DEFAULT 100.0
                )
            """);

            // Downloads table
            st.execute("""
                CREATE TABLE IF NOT EXISTS Downloads (
                    account_id       INTEGER NOT NULL,
                    game_id          INTEGER NOT NULL,
                    download_count   INTEGER NOT NULL DEFAULT 0,
                    last_downloaded  TEXT,
                    PRIMARY KEY (account_id, game_id),
                    FOREIGN KEY (account_id) REFERENCES Accounts(id),
                    FOREIGN KEY (game_id)    REFERENCES Games(id)
                )
            """);

            Logger.info("DatabaseManager", "Schema verified/created.");
        }
    }

    // ─── Game Operations ──────────────────────────────────────────────────────

    public synchronized void insertGame(Game game) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO Games (title, developer, genre, price, description, file_path)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, game.getTitle());
            ps.setString(2, game.getDeveloper());
            ps.setString(3, game.getGenre());
            ps.setDouble(4, game.getPrice());
            ps.setString(5, game.getDescription());
            ps.setString(6, game.getFilePath());
            ps.executeUpdate();
        }
    }

    public synchronized List<Game> getAllGames() throws SQLException {
        List<Game> games = new ArrayList<>();
        String sql = "SELECT id, title, developer, genre, price, description, file_path FROM Games";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                games.add(mapGame(rs));
            }
        }
        return games;
    }

    public synchronized Game getGameById(int id) throws SQLException {
        String sql = "SELECT id, title, developer, genre, price, description, file_path FROM Games WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapGame(rs);
            }
        }
        return null;
    }

    private Game mapGame(ResultSet rs) throws SQLException {
        return new Game(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("developer"),
            rs.getString("genre"),
            rs.getDouble("price"),
            rs.getString("description"),
            rs.getString("file_path")
        );
    }

    // ─── Account Operations ───────────────────────────────────────────────────

    public synchronized boolean registerUser(String username, String passwordHash, String dob) throws SQLException {
        String sql = "INSERT OR IGNORE INTO Accounts (username, password_hash, date_of_birth) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, dob);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized UserAccount loginUser(String username, String passwordHash) throws SQLException {
        String sql = "SELECT id, username, password_hash, date_of_birth, balance FROM Accounts WHERE username=? AND password_hash=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserAccount(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("date_of_birth"),
                        rs.getDouble("balance")
                    );
                }
            }
        }
        return null;
    }

    public synchronized double getBalance(int accountId) throws SQLException {
        String sql = "SELECT balance FROM Accounts WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        }
        return 0;
    }

    public synchronized boolean deductBalance(int accountId, double amount) throws SQLException {
        connection.setAutoCommit(false);
        try {
            double current = getBalance(accountId);
            if (current < amount) {
                connection.rollback();
                return false;
            }
            String sql = "UPDATE Accounts SET balance = balance - ? WHERE id=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setInt(2, accountId);
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ─── Download Operations ──────────────────────────────────────────────────

    public synchronized void recordDownload(int accountId, int gameId) throws SQLException {
        String sql = """
            INSERT INTO Downloads (account_id, game_id, download_count, last_downloaded)
            VALUES (?, ?, 1, datetime('now'))
            ON CONFLICT(account_id, game_id) DO UPDATE SET
                download_count  = download_count + 1,
                last_downloaded = datetime('now')
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, gameId);
            ps.executeUpdate();
        }
    }

    public synchronized boolean hasDownloaded(int accountId, int gameId) throws SQLException {
        String sql = "SELECT 1 FROM Downloads WHERE account_id=? AND game_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public synchronized int getDownloadCount(int accountId, int gameId) throws SQLException {
        String sql = "SELECT download_count FROM Downloads WHERE account_id=? AND game_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("download_count") : 0;
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Logger.info("DatabaseManager", "Database connection closed.");
            }
        } catch (SQLException e) {
            Logger.error("DatabaseManager", "Error closing DB: " + e.getMessage());
        }
    }
}
