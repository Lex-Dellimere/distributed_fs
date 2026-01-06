package org.example.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientManager {
    private final AtomicInteger nextClientId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ClientInfo> clients = new ConcurrentHashMap<>();

    public ClientInfo registerClient(String username, String role) {

        int clientId = nextClientId.getAndIncrement();
        ClientInfo clientInfo = new ClientInfo(clientId, username, role);
        clients.put(clientId, clientInfo);

        System.out.println("Client #" + clientId + " registered: " + username + " (" + role + ")");
        return clientInfo;
    }

    public void unregisterClient(int clientId) {

        ClientInfo client = clients.remove(clientId);
        if (client != null) {
            System.out.println("Client #" + clientId + " unregistered: " + client.username());
        }
    }

    public ClientInfo getClient(int clientId) {
        return clients.get(clientId);
    }

    public String listConnectedClients() {

        if (clients.isEmpty()) {
            return "No clients connected";
        }

        StringBuilder sb = new StringBuilder("Connected clients:\n");
        for (ClientInfo client : clients.values()) {
            sb.append("  Client #").append(client.clientId())
                    .append(": ").append(client.username())
                    .append(" [").append(client.role()).append("]\n");
        }
        return sb.toString().trim();
    }

    public int getClientCount() {
        return clients.size();
    }
}
