package steamlite.model;

import java.io.Serializable;

/**
 * Structured message exchanged between client and server over the socket.
 * All communication uses serialized Message objects for type safety.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // Auth
        REGISTER, LOGIN, LOGOUT,
        // Catalog
        LIST_GAMES, GAME_DETAIL,
        // Download
        DOWNLOAD_REQUEST, DOWNLOAD_CHUNK, DOWNLOAD_COMPLETE, DOWNLOAD_PROGRESS,
        // Wallet
        GET_BALANCE,
        // Responses
        SUCCESS, ERROR, INFO
    }

    private final Type   type;
    private final String payload;   // JSON or text data
    private final byte[] data;      // Binary chunk for file transfers

    public Message(Type type, String payload) {
        this.type    = type;
        this.payload = payload;
        this.data    = null;
    }

    public Message(Type type, String payload, byte[] data) {
        this.type    = type;
        this.payload = payload;
        this.data    = data;
    }

    public Type   getType()    { return type; }
    public String getPayload() { return payload; }
    public byte[] getData()    { return data; }

    @Override
    public String toString() {
        return "Message{type=" + type + ", payload='" + payload + "'}";
    }
}
