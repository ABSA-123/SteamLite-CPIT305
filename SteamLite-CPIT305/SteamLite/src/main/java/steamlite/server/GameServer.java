package steamlite.server;

import steamlite.db.DatabaseManager;
import steamlite.util.GameLoader;
import steamlite.util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded TCP server for Steam Lite.
 *
 * Architecture:
 *  - One ServerSocket listening on a fixed port
 *  - A fixed thread pool (ExecutorService) to manage concurrent client sessions
 *  - Each accepted connection dispatches a ClientHandler Runnable to the pool
 *  - Graceful shutdown hook ensures DB and pool are closed cleanly
 */
public class GameServer {

    public  static final int    PORT            = 9090;
    private static final int    MAX_THREADS     = 10;
    private static final String RESOURCES_DIR   = "resources";

    private final ServerSocket    serverSocket;
    private final ExecutorService threadPool;
    private final DatabaseManager db;
    private final AtomicInteger   clientCounter = new AtomicInteger(0);
    private volatile boolean      running       = true;

    public GameServer() throws IOException, SQLException {
        // 1. Initialize database (schema auto-created)
        db = new DatabaseManager();

        // 2. Seed game catalog from resource files
        GameLoader loader = new GameLoader(RESOURCES_DIR, db);
        loader.loadGamesFromFiles();

        // 3. Create thread pool
        threadPool = Executors.newFixedThreadPool(MAX_THREADS);

        // 4. Bind server socket
        serverSocket = new ServerSocket(PORT);
        serverSocket.setReuseAddress(true);

        Logger.info("GameServer", "Server started on port " + PORT
            + " | Thread pool size: " + MAX_THREADS);

        // 5. Register shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ShutdownHook"));
    }

    /**
     * Main accept loop – blocks waiting for client connections.
     */
    public void start() {
        Logger.info("GameServer", "Waiting for client connections...");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                int id = clientCounter.incrementAndGet();
                Logger.info("GameServer", "Accepted client #" + id
                    + " from " + clientSocket.getRemoteSocketAddress());

                // Dispatch to thread pool – non-blocking for the accept loop
                threadPool.execute(new ClientHandler(clientSocket, db, id));

            } catch (IOException e) {
                if (running) {
                    Logger.error("GameServer", "Accept error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Graceful shutdown: stop accepting, await running handlers, close resources.
     */
    private void shutdown() {
        Logger.info("GameServer", "Shutting down...");
        running = false;

        // Close server socket to unblock accept()
        try {
            if (!serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            Logger.error("GameServer", "Error closing server socket: " + e.getMessage());
        }

        // Wait for active client handlers to finish
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                Logger.warn("GameServer", "Forced thread pool shutdown after timeout.");
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close database connection
        db.close();
        Logger.info("GameServer", "Server shutdown complete.");
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            GameServer server = new GameServer();
            server.start();
        } catch (IOException | SQLException e) {
            Logger.error("GameServer", "Fatal startup error: " + e.getMessage());
            System.exit(1);
        }
    }
}
