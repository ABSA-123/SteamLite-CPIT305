package steamlite.client;

import steamlite.model.Message;
import steamlite.util.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Scanner;

/**
 * Interactive command-line client for Steam Lite.
 *
 * Connects to GameServer over TCP and communicates using serialized Message objects.
 * Received file chunks are written to a local downloads/ directory.
 */
public class GameClient {

    private static final String DEFAULT_HOST     = "localhost";
    private static final int    DEFAULT_PORT      = 9090;
    private static final String DOWNLOAD_DIR      = "downloads";

    private final String host;
    private final int    port;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private boolean            connected = false;

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out    = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.flush();
        in     = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        connected = true;

        // Ensure downloads directory exists
        Files.createDirectories(Paths.get(DOWNLOAD_DIR));
        Logger.info("GameClient", "Connected to " + host + ":" + port);
    }

    public void disconnect() {
        try {
            connected = false;
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            Logger.error("GameClient", "Disconnect error: " + e.getMessage());
        }
    }

    // ─── Send / Receive ───────────────────────────────────────────────────────

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
        out.reset();
    }

    private Message receive() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    // ─── Command Methods ──────────────────────────────────────────────────────

    public void register(String username, String password, String dob) throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.REGISTER, username + "|" + password + "|" + dob));
        printResponse(receive());
    }

    public void login(String username, String password) throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.LOGIN, username + "|" + password));
        printResponse(receive());
    }

    public void logout() throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.LOGOUT, ""));
        printResponse(receive());
    }

    public void listGames() throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.LIST_GAMES, ""));
        printResponse(receive());
    }

    public void gameDetail(int gameId) throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.GAME_DETAIL, String.valueOf(gameId)));
        printResponse(receive());
    }

    public void getBalance() throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.GET_BALANCE, ""));
        printResponse(receive());
    }

    /**
     * Requests a game download and writes received chunks to a local file.
     * Displays live progress as chunks arrive.
     */
    public void downloadGame(int gameId) throws IOException, ClassNotFoundException {
        send(new Message(Message.Type.DOWNLOAD_REQUEST, String.valueOf(gameId)));

        // First response: START or ERROR
        Message first = receive();
        if (first.getType() == Message.Type.ERROR) {
            printResponse(first);
            return;
        }

        // Parse "START|title|fileSize"
        String[] parts    = first.getPayload().split("\\|", 3);
        String   title    = parts.length > 1 ? parts[1] : "game";
        long     fileSize = parts.length > 2 ? Long.parseLong(parts[2]) : 0;

        String safeName   = title.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".bin";
        Path   outputPath = Paths.get(DOWNLOAD_DIR, safeName);

        System.out.println("\n Downloading: " + title + " (" + fileSize + " bytes)");
        System.out.println(" Saving to:   " + outputPath.toAbsolutePath());

        long written = 0;

        // Receive chunks until DOWNLOAD_COMPLETE
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            Message msg;
            while ((msg = receive()).getType() == Message.Type.DOWNLOAD_CHUNK) {
                byte[] chunk = msg.getData();
                if (chunk != null) {
                    bos.write(chunk);
                    written += chunk.length;
                }
                // Parse progress
                String progress = msg.getPayload();
                String pct = "?";
                for (String kv : progress.split("\\|")) {
                    if (kv.startsWith("progress=")) pct = kv.split("=")[1];
                }
                printProgressBar(Integer.parseInt(pct));
            }

            // Final message should be DOWNLOAD_COMPLETE
            System.out.println(); // newline after progress bar
            printResponse(msg);
        }

        System.out.println(" File saved: " + outputPath + " (" + written + " bytes written)");
    }

    // ─── CLI UI ───────────────────────────────────────────────────────────────

    private void printProgressBar(int pct) {
        int filled = pct / 5;
        StringBuilder bar = new StringBuilder("\r [");
        for (int i = 0; i < 20; i++) bar.append(i < filled ? "█" : "░");
        bar.append("] ").append(pct).append("%");
        System.out.print(bar);
        System.out.flush();
    }

    private void printResponse(Message msg) {
        String prefix = switch (msg.getType()) {
            case SUCCESS, DOWNLOAD_COMPLETE -> "✔ ";
            case ERROR                      -> "✘ ";
            case INFO                       -> "ℹ ";
            default                         -> "  ";
        };
        System.out.println("\n" + prefix + msg.getPayload());
    }

    // ─── Interactive REPL ─────────────────────────────────────────────────────

    private void runInteractive() {
        try (Scanner scanner = new Scanner(System.in)) {
            printHelp();

            while (connected) {
                System.out.print("\nsteamlite> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isBlank()) continue;

                String[] tokens = line.split("\\s+", 2);
                String   cmd    = tokens[0].toLowerCase();
                String   args   = tokens.length > 1 ? tokens[1] : "";

                try {
                    switch (cmd) {
                        case "register" -> {
                            String[] parts = args.split("\\s+");
                            if (parts.length < 3) {
                                System.out.println("Usage: register <username> <password> <dob>");
                            } else {
                                register(parts[0], parts[1], parts[2]);
                            }
                        }
                        case "login" -> {
                            String[] parts = args.split("\\s+");
                            if (parts.length < 2) {
                                System.out.println("Usage: login <username> <password>");
                            } else {
                                login(parts[0], parts[1]);
                            }
                        }
                        case "logout"   -> logout();
                        case "list"     -> listGames();
                        case "detail"   -> {
                            if (args.isBlank()) System.out.println("Usage: detail <gameId>");
                            else gameDetail(Integer.parseInt(args.trim()));
                        }
                        case "download" -> {
                            if (args.isBlank()) System.out.println("Usage: download <gameId>");
                            else downloadGame(Integer.parseInt(args.trim()));
                        }
                        case "balance"  -> getBalance();
                        case "help"     -> printHelp();
                        case "quit", "exit" -> {
                            System.out.println("Goodbye!");
                            disconnect();
                            return;
                        }
                        default -> System.out.println("Unknown command. Type 'help' for options.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("✘ Please enter a valid number.");
                } catch (IOException | ClassNotFoundException e) {
                    Logger.error("GameClient", "Communication error: " + e.getMessage());
                    System.out.println("✘ Lost connection to server.");
                    connected = false;
                }
            }
        }
    }

    private void printHelp() {
        System.out.println("""
            ╔══════════════════════════════════════════════════╗
            ║           Steam Lite - Client Commands           ║
            ╠══════════════════════════════════════════════════╣
            ║  register <user> <pass> <dob>  Create account   ║
            ║  login    <user> <pass>         Log in           ║
            ║  logout                         Log out          ║
            ║  list                           Browse games     ║
            ║  detail   <id>                  Game info        ║
            ║  download <id>                  Buy & download   ║
            ║  balance                        Check wallet     ║
            ║  help                           Show this menu   ║
            ║  quit                           Exit             ║
            ╚══════════════════════════════════════════════════╝
            """);
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        GameClient client = new GameClient(host, port);
        try {
            client.connect();
            client.runInteractive();
        } catch (IOException e) {
            Logger.error("GameClient", "Could not connect to server: " + e.getMessage());
            System.err.println("✘ Cannot reach server at " + host + ":" + port
                + ". Is it running?");
        } finally {
            client.disconnect();
        }
    }
}
