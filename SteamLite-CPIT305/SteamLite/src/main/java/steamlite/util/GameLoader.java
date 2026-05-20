package steamlite.util;

import steamlite.db.DatabaseManager;
import steamlite.model.Game;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.List;

/**
 * Reads game catalog data from the resources/games directory and seeds the database.
 * Each game has a .txt metadata file and a .bin asset file.
 *
 * Metadata format (one field per line):
 *   title=<value>
 *   developer=<value>
 *   genre=<value>
 *   price=<value>
 *   description=<value>
 */
public class GameLoader {

    private final String resourcesDir;
    private final DatabaseManager db;

    public GameLoader(String resourcesDir, DatabaseManager db) {
        this.resourcesDir = resourcesDir;
        this.db           = db;
    }

    /**
     * Scans the games directory for .txt metadata files and seeds the DB.
     */
    public void loadGamesFromFiles() {
        Path dir = Paths.get(resourcesDir, "games");
        if (!Files.exists(dir)) {
            Logger.warn("GameLoader", "Games directory not found: " + dir);
            return;
        }

        try {
            List<Path> metaFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".txt"))
                .toList();

            for (Path file : metaFiles) {
                loadSingleGame(file);
            }
            Logger.info("GameLoader", "Loaded " + metaFiles.size() + " game(s) from files.");
        } catch (IOException e) {
            Logger.error("GameLoader", "Failed to scan games directory: " + e.getMessage());
        }
    }

    private void loadSingleGame(Path metaFile) {
        String title = "", developer = "", genre = "", description = "";
        double price = 0.0;

        // IO Stream: read metadata with BufferedReader
        try (BufferedReader reader = Files.newBufferedReader(metaFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.contains("=")) continue;
                String key   = line.substring(0, line.indexOf('=')).trim().toLowerCase();
                String value = line.substring(line.indexOf('=') + 1).trim();
                switch (key) {
                    case "title"       -> title       = value;
                    case "developer"   -> developer   = value;
                    case "genre"       -> genre       = value;
                    case "price"       -> price       = Double.parseDouble(value);
                    case "description" -> description = value;
                }
            }
        } catch (IOException e) {
            Logger.error("GameLoader", "Error reading file " + metaFile + ": " + e.getMessage());
            return;
        }

        // Derive asset file path (same name, .bin extension)
        String assetPath = metaFile.toString().replace(".txt", ".bin");

        // Create asset file if it doesn't exist (simulate a game binary)
        createAssetIfMissing(assetPath, title);

        Game game = new Game(0, title, developer, genre, price, description, assetPath);
        try {
            db.insertGame(game);
            Logger.info("GameLoader", "Seeded game: " + title);
        } catch (SQLException e) {
            Logger.error("GameLoader", "DB insert failed for " + title + ": " + e.getMessage());
        }
    }

    /**
     * Creates a simulated binary game asset file if it doesn't exist.
     */
    private void createAssetIfMissing(String assetPath, String title) {
        File asset = new File(assetPath);
        if (!asset.exists()) {
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(asset))) {
                // Write a small simulated "game binary" header
                dos.writeUTF("STEAMLITE_GAME_ASSET");
                dos.writeUTF(title);
                dos.writeLong(System.currentTimeMillis());
                // Pad to simulate a non-trivial file (~10KB)
                byte[] padding = new byte[10240];
                for (int i = 0; i < padding.length; i++) padding[i] = (byte)(i % 256);
                dos.write(padding);
                Logger.info("GameLoader", "Created asset file: " + assetPath);
            } catch (IOException e) {
                Logger.error("GameLoader", "Failed to create asset: " + e.getMessage());
            }
        }
    }
}
