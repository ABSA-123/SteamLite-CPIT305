package steamlite.model;

import java.io.Serializable;

/**
 * Represents a game in the Steam Lite catalog.
 */
public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String title;
    private final String developer;
    private final String genre;
    private final double price;
    private final String description;
    private final String filePath;

    public Game(int id, String title, String developer, String genre,
                double price, String description, String filePath) {
        this.id          = id;
        this.title       = title;
        this.developer   = developer;
        this.genre       = genre;
        this.price       = price;
        this.description = description;
        this.filePath    = filePath;
    }

    public int    getId()          { return id; }
    public String getTitle()       { return title; }
    public String getDeveloper()   { return developer; }
    public String getGenre()       { return genre; }
    public double getPrice()       { return price; }
    public String getDescription() { return description; }
    public String getFilePath()    { return filePath; }

    @Override
    public String toString() {
        return String.format("[%d] %s  |  %s  |  %s  |  $%.2f%n    %s",
            id, title, developer, genre, price, description);
    }
}
