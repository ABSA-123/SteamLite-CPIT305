package steamlite.tests;

import steamlite.db.DatabaseManager;
import steamlite.model.Game;
import steamlite.model.UserAccount;
import steamlite.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * Unit tests for Steam Lite core components.
 *
 * Uses a lightweight custom test runner (no external dependencies needed).
 * Tests cover DatabaseManager CRUD, PasswordUtil hashing, and download logic.
 */
public class SteamLiteTests {

    // ─── Simple Test Framework ────────────────────────────────────────────────

    private static int passed = 0;
    private static int failed = 0;

    private static void assertTrue(String name, boolean condition) {
        if (condition) {
            System.out.println("  ✔ PASS: " + name);
            passed++;
        } else {
            System.out.println("  ✘ FAIL: " + name);
            failed++;
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            System.out.println("  ✔ PASS: " + name);
            passed++;
        } else {
            System.out.println("  ✘ FAIL: " + name + " [expected=" + expected + ", actual=" + actual + "]");
            failed++;
        }
    }

    // ─── Test Cases ───────────────────────────────────────────────────────────

    private static void testPasswordHashing() {
        System.out.println("\n[TEST] PasswordUtil");

        String hash1 = PasswordUtil.hash("secret123");
        String hash2 = PasswordUtil.hash("secret123");
        String hash3 = PasswordUtil.hash("different");

        assertTrue("Hash is not null",         hash1 != null);
        assertTrue("Hash length is 64 chars",  hash1.length() == 64);
        assertTrue("Same input = same hash",   hash1.equals(hash2));
        assertTrue("Different input != same",  !hash1.equals(hash3));
        assertTrue("Hash is lowercase hex",    hash1.matches("[0-9a-f]{64}"));
    }

    private static void testDatabaseOperations() throws SQLException {
        System.out.println("\n[TEST] DatabaseManager");

        // Use an in-memory test DB
        System.setProperty("steamlite.test.db", ":memory:");
        DatabaseManager db = new DatabaseManager();

        // ── Game CRUD ──
        Game g = new Game(0, "Test Game", "TestDev", "Action",
                          14.99, "A test game.", "resources/games/test.bin");
        db.insertGame(g);

        List<Game> games = db.getAllGames();
        assertTrue("Game inserted",             games.size() >= 1);
        assertTrue("Game title correct",        games.stream().anyMatch(x -> x.getTitle().equals("Test Game")));

        // ── Duplicate insert (should be ignored) ──
        db.insertGame(g);
        List<Game> games2 = db.getAllGames();
        assertEquals("Duplicate not inserted",  games.size(), games2.size());

        // ── Account registration ──
        boolean reg = db.registerUser("alice", PasswordUtil.hash("pass1"), "2000-01-01");
        assertTrue("Alice registered", reg);

        boolean dupReg = db.registerUser("alice", PasswordUtil.hash("pass2"), "2000-01-01");
        assertTrue("Duplicate username rejected", !dupReg);

        // ── Login ──
        UserAccount user = db.loginUser("alice", PasswordUtil.hash("pass1"));
        assertTrue("Login succeeds with correct creds",   user != null);
        assertEquals("Username matches",                  "alice", user.getUsername());

        UserAccount badUser = db.loginUser("alice", PasswordUtil.hash("wrongpass"));
        assertTrue("Login fails with wrong password", badUser == null);

        // ── Balance ──
        double balance = db.getBalance(user.getId());
        assertTrue("Default balance is 100.0", balance == 100.0);

        // Deduct within budget
        boolean charged = db.deductBalance(user.getId(), 14.99);
        assertTrue("Deduction succeeds",        charged);
        double afterCharge = db.getBalance(user.getId());
        assertTrue("Balance reduced correctly", Math.abs(afterCharge - 85.01) < 0.01);

        // Deduct beyond balance
        boolean overCharge = db.deductBalance(user.getId(), 9999.0);
        assertTrue("Overdraft rejected", !overCharge);

        // ── Downloads ──
        int gameId = games.get(0).getId();
        db.recordDownload(user.getId(), gameId);
        assertTrue("Download recorded",           db.hasDownloaded(user.getId(), gameId));
        assertEquals("Download count = 1",        1, db.getDownloadCount(user.getId(), gameId));

        // Record again (re-download)
        db.recordDownload(user.getId(), gameId);
        assertEquals("Download count = 2",        2, db.getDownloadCount(user.getId(), gameId));

        // Non-downloaded game
        assertTrue("hasDownloaded false for other game", !db.hasDownloaded(user.getId(), 9999));

        db.close();
    }

    private static void testGameRetrieval() throws SQLException {
        System.out.println("\n[TEST] Game Retrieval");

        DatabaseManager db = new DatabaseManager();
        db.insertGame(new Game(0, "Alpha", "Dev1", "RPG", 5.0, "Desc", "path/a.bin"));
        db.insertGame(new Game(0, "Beta",  "Dev2", "FPS", 10.0, "Desc", "path/b.bin"));

        List<Game> all = db.getAllGames();
        assertTrue("At least 2 games retrieved", all.size() >= 2);

        // getById
        int id = all.get(0).getId();
        Game found = db.getGameById(id);
        assertTrue("getById returns game",   found != null);
        assertEquals("getById ID matches",   id, found.getId());

        Game notFound = db.getGameById(999999);
        assertTrue("getById returns null for missing", notFound == null);

        db.close();
    }

    // ─── Runner ───────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("        Steam Lite Unit Tests          ");
        System.out.println("═══════════════════════════════════════");

        try {
            testPasswordHashing();
            testDatabaseOperations();
            testGameRetrieval();
        } catch (Exception e) {
            System.out.println("\n✘ EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n═══════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═══════════════════════════════════════");

        System.exit(failed > 0 ? 1 : 0);
    }
}
