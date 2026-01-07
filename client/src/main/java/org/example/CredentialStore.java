package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CredentialStore {
    private static final String STORE_FILE = System.getProperty("user.home") + "/.dfs_credentials.json";
    private final ObjectMapper mapper = new ObjectMapper();

    public void saveCredentials(String username, String hashedPassword) {
        try {
            Map<String, String> creds = new HashMap<>();
            creds.put("username", username);
            creds.put("password", hashedPassword);
            mapper.writeValue(new File(STORE_FILE), creds);
        } catch (IOException e) {
            System.err.println("Failed to save credentials: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> loadCredentials() {
        try {
            File file = new File(STORE_FILE);
            if (file.exists()) {
                Map<String, String> m = mapper.readValue(file, Map.class);
                return m != null ? m : new HashMap<>();
            }
        } catch (IOException e) {
            System.err.println("Failed to load credentials: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public void clearCredentials() {
        try {
            Files.deleteIfExists(Paths.get(STORE_FILE));
        } catch (IOException e) {
            System.err.println("Failed to clear credentials: " + e.getMessage());
        }
    }

    public boolean hasStoredCredentials() {
        return new File(STORE_FILE).exists();
    }
}
