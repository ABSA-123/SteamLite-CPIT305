package steamlite.server;

import steamlite.db.DatabaseManager;
import steamlite.model.Game;
import steamlite.model.Message;
import steamlite.model.UserAccount;
import steamlite.util.Logger;
import steamlite.util.PasswordUtil;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

/**
 * Handles one client's session in a dedicated thread.
 * Each ClientHandler is a Runnable assigned to the server's thread pool.
 *
 * Thread safety: DatabaseManager methods are synchronized; per-session
 * state (currentUser) lives only in this instance.
 */
public class ClientHandler implements Runnable {

    private static final int CHUNK_SIZE = 4096; // bytes per transfer chunk

    private final Socket         socket;
    private final DatabaseManager db;
    private final int            clientId;

    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private UserAccount        currentUser;   // null = not logged in

    public ClientHandler(Socket socket, DatabaseManager db, int clientId) {
        this.socket   = socket;
        this.db       = db;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        Logger.info("ClientHandler-" + clientId,
            "New connection from " + socket.getRemoteSocketAddress());

        try {
            // Open streams (ObjectOutputStream first to avoid deadlock)
            out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.flush();
            in  = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            // Main message dispatch loop
            Message msg;
            while ((msg = receive()) != null) {
                process(msg);
            }

        } catch (EOFException | java.net.SocketException e) {
            Logger.info("ClientHandler-" + clientId, "Client disconnected.");
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("ClientHandler-" + clientId, "Stream error: " + e.getMessage());
        } finally {
            closeResources();
        }
    }

    // ─── Message Dispatch ─────────────────────────────────────────────────────

    private void process(Message msg) throws IOException {
        Logger.info("ClientHandler-" + clientId, "Processing: " + msg.getType());
        try {
            switch (msg.getType()) {
                case REGISTER        -> handleRegister(msg);
                case LOGIN           -> handleLogin(msg);
                case LOGOUT          -> handleLogout();
                case LIST_GAMES      -> handleListGames();
                case GAME_DETAIL     -> handleGameDetail(msg);
                case DOWNLOAD_REQUEST -> handleDownload(msg);
                case GET_BALANCE     -> handleGetBalance();
                default              -> send(new Message(Message.Type.ERROR, "Unknown command."));
            }
        } catch (SQLException e) {
            Logger.error("ClientHandler-" + clientId, "DB error: " + e.getMessage());
            send(new Message(Message.Type.ERROR, "Server database error: " + e.getMessage()));
        }
    }

    // ─── Auth Handlers ────────────────────────────────────────────────────────

    private void handleRegister(Message msg) throws SQLException, IOException {
        // payload: "username|plainPassword|dob"
        String[] parts = msg.getPayload().split("\\|", 3);
        if (parts.length != 3) { send(error("Invalid register format.")); return; }

        String username = parts[0].trim();
        String hash     = PasswordUtil.hash(parts[1].trim());
        String dob      = parts[2].trim();

        boolean ok = db.registerUser(username, hash, dob);
        if (ok) {
            Logger.info("ClientHandler-" + clientId, "Registered user: " + username);
            send(new Message(Message.Type.SUCCESS, "Account created for " + username + ". You may now log in."));
        } else {
            send(error("Username '" + username + "' is already taken."));
        }
    }

    private void handleLogin(Message msg) throws SQLException, IOException {
        if (currentUser != null) { send(error("Already logged in as " + currentUser.getUsername())); return; }

        // payload: "username|plainPassword"
        String[] parts = msg.getPayload().split("\\|", 2);
        if (parts.length != 2) { send(error("Invalid login format.")); return; }

        String username = parts[0].trim();
        String hash     = PasswordUtil.hash(parts[1].trim());

        UserAccount user = db.loginUser(username, hash);
        if (user != null) {
            currentUser = user;
            Logger.info("ClientHandler-" + clientId, "Logged in: " + username);
            send(new Message(Message.Type.SUCCESS,
                "Welcome back, " + username + "! Balance: $" + String.format("%.2f", user.getBalance())));
        } else {
            send(error("Invalid username or password."));
        }
    }

    private void handleLogout() throws IOException {
        if (currentUser != null) {
            Logger.info("ClientHandler-" + clientId, "Logged out: " + currentUser.getUsername());
            currentUser = null;
        }
        send(new Message(Message.Type.SUCCESS, "Logged out successfully."));
    }

    // ─── Catalog Handlers ─────────────────────────────────────────────────────

    private void handleListGames() throws SQLException, IOException {
        List<Game> games = db.getAllGames();
        if (games.isEmpty()) {
            send(new Message(Message.Type.INFO, "No games available in the catalog."));
            return;
        }
        StringBuilder sb = new StringBuilder("=== Game Catalog ===\n");
        for (Game g : games) sb.append(g.toString()).append("\n");
        send(new Message(Message.Type.SUCCESS, sb.toString()));
    }

    private void handleGameDetail(Message msg) throws SQLException, IOException {
        int gameId = parseId(msg.getPayload());
        if (gameId < 0) { send(error("Invalid game ID.")); return; }

        Game game = db.getGameById(gameId);
        if (game == null) { send(error("Game not found.")); return; }

        String detail = game.toString();
        if (currentUser != null) {
            int count = db.getDownloadCount(currentUser.getId(), gameId);
            detail += "\n[Your downloads: " + count + "]";
        }
        send(new Message(Message.Type.SUCCESS, detail));
    }

    // ─── Download Handler (File I/O + chunked transfer) ───────────────────────

    private void handleDownload(Message msg) throws SQLException, IOException {
        if (currentUser == null) { send(error("Please log in first.")); return; }

        int gameId = parseId(msg.getPayload());
        if (gameId < 0) { send(error("Invalid game ID.")); return; }

        Game game = db.getGameById(gameId);
        if (game == null) { send(error("Game not found.")); return; }

        File gameFile = new File(game.getFilePath());
        if (!gameFile.exists()) { send(error("Game asset missing on server.")); return; }

        // Check if user already owns it; if not, charge them
        boolean alreadyOwned = db.hasDownloaded(currentUser.getId(), gameId);
        if (!alreadyOwned) {
            double price = game.getPrice();
            boolean paid = db.deductBalance(currentUser.getId(), price);
            if (!paid) {
                send(error("Insufficient balance. Price: $" + String.format("%.2f", price)
                    + " | Your balance: $" + String.format("%.2f", db.getBalance(currentUser.getId()))));
                return;
            }
            Logger.info("ClientHandler-" + clientId,
                "Charged $" + price + " to " + currentUser.getUsername() + " for " + game.getTitle());
        }

        // Record download
        db.recordDownload(currentUser.getId(), gameId);

        // Stream file to client in chunks
        long fileSize = gameFile.length();
        send(new Message(Message.Type.INFO, "START|" + game.getTitle() + "|" + fileSize));

        try (FileInputStream fis = new FileInputStream(gameFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            byte[] buffer = new byte[CHUNK_SIZE];
            long   sent   = 0;
            int    read;

            while ((read = bis.read(buffer)) != -1) {
                byte[] chunk = (read == CHUNK_SIZE) ? buffer : java.util.Arrays.copyOf(buffer, read);
                sent += read;
                int progress = (int)((sent * 100) / fileSize);
                send(new Message(Message.Type.DOWNLOAD_CHUNK,
                    "progress=" + progress + "|sent=" + sent + "|total=" + fileSize,
                    chunk));
            }
        }

        send(new Message(Message.Type.DOWNLOAD_COMPLETE,
            "Download of '" + game.getTitle() + "' complete. " +
            (alreadyOwned ? "(Already owned - no charge)" : "Charged: $" + game.getPrice())));

        Logger.info("ClientHandler-" + clientId,
            currentUser.getUsername() + " downloaded " + game.getTitle());
    }

    // ─── Balance Handler ──────────────────────────────────────────────────────

    private void handleGetBalance() throws SQLException, IOException {
        if (currentUser == null) { send(error("Please log in first.")); return; }
        double balance = db.getBalance(currentUser.getId());
        send(new Message(Message.Type.SUCCESS, "Balance: $" + String.format("%.2f", balance)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
        out.reset(); // prevent object caching across sends
    }

    private Message receive() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    private Message error(String text) {
        return new Message(Message.Type.ERROR, text);
    }

    private int parseId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private void closeResources() {
        try { if (in  != null) in.close();     } catch (IOException ignored) {}
        try { if (out != null) out.close();    } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException ignored) {}
        Logger.info("ClientHandler-" + clientId, "Resources closed.");
    }
}
