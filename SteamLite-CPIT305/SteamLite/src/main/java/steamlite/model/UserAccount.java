package steamlite.model;

import java.io.Serializable;

/**
 * Represents a registered user account.
 */
public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String username;
    private final String passwordHash;
    private final String dateOfBirth;
    private       double balance;

    public UserAccount(int id, String username, String passwordHash,
                       String dateOfBirth, double balance) {
        this.id           = id;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.dateOfBirth  = dateOfBirth;
        this.balance      = balance;
    }

    public int    getId()           { return id; }
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDateOfBirth()  { return dateOfBirth; }
    public double getBalance()      { return balance; }
    public void   setBalance(double b) { this.balance = b; }
}
