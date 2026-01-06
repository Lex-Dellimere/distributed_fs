package org.example.config;

import java.io.File;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerConfig {
    private int port = 5555;
    private String resourcesPath = "dfs_root";
    private DatabaseConfig db;
    private String adminUser;
    private String adminPass;

    public void save() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File("server_config.json"), this);
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    public static ServerConfig load() {
        File configFile = new File("server_config.json");
        if (!configFile.exists()) {
            return new ServerConfig();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(configFile, ServerConfig.class);
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            return new ServerConfig();
        }
    }

    // Getters and Setters
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getResourcesPath() { return resourcesPath; }
    public void setResourcesPath(String resourcesPath) { this.resourcesPath = resourcesPath; }
    public DatabaseConfig getDb() { return db; }
    public void setDb(DatabaseConfig db) { this.db = db; }
    public String getAdminUser() { return adminUser; }
    public void setAdminUser(String adminUser) { this.adminUser = adminUser; }
    public String getAdminPass() { return adminPass; }
    public void setAdminPass(String adminPass) { this.adminPass = adminPass; }
}
