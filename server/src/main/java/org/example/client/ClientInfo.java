package org.example.client;

import java.util.Date;

public class ClientInfo {
    private final int clientId;
    private final String username;
    private final Date connectedAt;
    private String currentDirectory;

    public ClientInfo(int clientId, String username) {
        this.clientId = clientId;
        this.username = username;
        this.connectedAt = new Date();
        this.currentDirectory = "/";
    }


    public int getClientId() { return clientId; }
    public String getUsername() { return username; }

    public Date getConnectedAt() {
        return connectedAt;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(String dir) {
        this.currentDirectory = dir;
    }

    @Override
    public String toString() {
        return String.format("Client #%d: %s (connected: %s)",
                clientId, username, connectedAt);
    }
}
