package org.example.setup;

import org.example.config.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SetupTool {
    public static void main(String[] args) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            ServerConfig config = ServerConfig.load();

            System.out.println("Welcome to FreeDRS initial setup");

            System.out.print("Server port [" + config.getPort() + "]: ");
            String portStr = br.readLine().trim();
            if (!portStr.isEmpty()) {
                try { config.setPort(Integer.parseInt(portStr)); } catch (NumberFormatException ignored) {}
            }

            System.out.print("Server root (VFS) [" + config.getResourcesPath() + "]: ");
            String path = br.readLine().trim();
            if (!path.isEmpty()) config.setResourcesPath(path);

            System.out.print("Admin username [" + (config.getAdminUser() == null ? "admin" : config.getAdminUser()) + "]: ");
            String adminUser = br.readLine().trim();
            if (!adminUser.isEmpty()) config.setAdminUser(adminUser);

            System.out.print("Admin password (will be stored hashed) [leave blank to skip]: ");
            String adminPass = br.readLine().trim();
            if (!adminPass.isEmpty()) config.setAdminPass(adminPass);

            config.save();
            System.out.println("Configuration saved to server_config.json");

            // Initialize DB via DatabaseManager
            try {
                org.example.db.DatabaseManager dbManager = new org.example.db.DatabaseManager(config);
                dbManager.initialize();
                System.out.println("Database initialized and admin user created if provided.");
            } catch (Exception e) {
                System.err.println("DB initialization failed: " + e.getMessage());
            }

            System.out.println("Setup complete.");
        } catch (IOException e) {
            System.err.println("Setup failed: " + e.getMessage());
        }
    }
}
