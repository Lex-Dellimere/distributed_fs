package org.example.client;

public class ClientInfo {
    private final int clientId;
    private final String username;
    private final boolean authenticated; // Made final
    private String currentDir;

    public ClientInfo(int clientId, String username) {
        this.clientId = clientId;
        this.username = username;
        this.authenticated = true;
        this.currentDir = ".";
    }

    public int getClientId() { return clientId; }
    public String getUsername() { return username; }
    public boolean isAuthenticated() { return authenticated; }
    public String getCurrentDir() { return currentDir; }
    public void setCurrentDir(String currentDir) { this.currentDir = currentDir; }
}