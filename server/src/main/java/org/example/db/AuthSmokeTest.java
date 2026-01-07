package org.example.db;

import org.example.config.ServerConfig;

public class AuthSmokeTest {
    public static void main(String[] args) throws Exception {
        ServerConfig cfg = new ServerConfig();
        // Use an isolated sqlite file to avoid messing with production DB
        cfg.setDb(new org.example.config.DatabaseConfig("test_dfs_db.db"));
        DatabaseManager db = new DatabaseManager(cfg);
        db.initialize();

        String username = "smoketest_user";
        String plaintext = "mypassword";

        // Simulate client hashing the password and sending that hash at signup
        String clientHash = org.mindrot.jbcrypt.BCrypt.hashpw(plaintext, org.mindrot.jbcrypt.BCrypt.gensalt());
        boolean reg = db.registerUser(username, clientHash, "guest");
        System.out.println("register with client-hash returned: " + reg);

        // Now authenticate by sending the same hash (client-side stored hash)
        org.example.db.User u1 = db.authenticate(username, clientHash);
        System.out.println("authenticate with stored-hash returned: " + (u1 != null));

        // Now authenticate by sending plaintext (server should check BCrypt against stored-hash)
        org.example.db.User u2 = db.authenticate(username, plaintext);
        System.out.println("authenticate with plaintext returned: " + (u2 != null));
    }
}

