package org.example.client;

import java.util.Date;

/**
 * Immutable client information for DFS sessions.
 * All fields must be non-null.
 */
public record ClientInfo(
    int clientId,
    String username,
    String role,
    Date connectedAt,
    String currentDirectory
) {
    public ClientInfo(int clientId, String username, String role) {
        this(clientId, username, role, new Date(), "/");
    }
    public ClientInfo withCurrentDirectory(String dir) {
        return new ClientInfo(clientId, username, role, connectedAt, dir);
    }
    @Override
    public String toString() {
        return String.format("Client #%d: %s (connected: %s)", clientId, username, connectedAt);
    }
}
