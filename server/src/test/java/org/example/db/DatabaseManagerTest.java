package org.example.db;

import org.example.config.ServerConfig;
import org.example.config.DatabaseConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseManagerTest {
    private static final String TEST_DB = "test_unit_dfs.db";
    private DatabaseManager db;

    @Before
    public void setup() throws Exception {
        // remove existing test db
        Files.deleteIfExists(Path.of(TEST_DB));
        // create an empty file so DatabaseManager.initialize will pick this explicit path
        Files.createFile(Path.of(TEST_DB));

        ServerConfig cfg = new ServerConfig();
        cfg.setDb(new DatabaseConfig(TEST_DB));
        db = new DatabaseManager(cfg);
        db.initialize();
    }

    @After
    public void teardown() throws Exception {
        Files.deleteIfExists(Path.of(TEST_DB));
    }

    @Test
    public void signupWithClientHashAndAuthenticateBothWays() throws Exception {
        String username = "testuser1";
        String plaintext = "secretPW";
        String clientHash = org.mindrot.jbcrypt.BCrypt.hashpw(plaintext, org.mindrot.jbcrypt.BCrypt.gensalt());

        boolean registered = db.registerUser(username, clientHash, "guest");
        Assert.assertTrue("registration should succeed", registered);

        User u1 = db.authenticate(username, clientHash);
        Assert.assertNotNull("auth with stored hash should succeed", u1);

        User u2 = db.authenticate(username, plaintext);
        Assert.assertNotNull("auth with plaintext should succeed", u2);
    }
}
