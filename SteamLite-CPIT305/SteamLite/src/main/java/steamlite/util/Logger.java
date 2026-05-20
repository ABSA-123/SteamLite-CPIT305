package steamlite.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logger that writes to both console and a log file.
 * Satisfies the IO Streams requirement for logging transactions.
 */
public class Logger {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_FILE = "steamlite.log";
    private static PrintWriter fileWriter;

    static {
        try {
            fileWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        } catch (IOException e) {
            System.err.println("[Logger] Could not open log file: " + e.getMessage());
        }
    }

    public static synchronized void info(String context, String message) {
        log("INFO ", context, message);
    }

    public static synchronized void error(String context, String message) {
        log("ERROR", context, message);
    }

    public static synchronized void warn(String context, String message) {
        log("WARN ", context, message);
    }

    private static void log(String level, String context, String message) {
        String line = String.format("[%s] [%s] [%s] %s",
            LocalDateTime.now().format(FMT), level, context, message);
        System.out.println(line);
        if (fileWriter != null) {
            fileWriter.println(line);
        }
    }

    public static void close() {
        if (fileWriter != null) fileWriter.close();
    }
}
