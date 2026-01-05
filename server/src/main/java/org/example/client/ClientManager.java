package org.example.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientManager {
    private static final AtomicInteger nextClientId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ClientInfo> clients = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Integer> connectedClients = new CopyOnWriteArrayList<>();

    public ClientInfo registerClient(String username) {
        int clientId = nextClientId.getAndIncrement();
        ClientInfo clientInfo = new ClientInfo(clientId, username);
        clients.put(clientId, clientInfo);
        connectedClients.add(clientId);
        return clientInfo;
    }

    public void unregisterClient(int clientId) {
        clients.remove(clientId);
        connectedClients.remove(Integer.valueOf(clientId));
    }

    public String listOtherClients(int excludeClientId) {
        if (connectedClients.size() <= 1) {
            return "Only you";
        }

        StringBuilder sb = new StringBuilder();
        for (Integer clientId : connectedClients) {
            if (clientId != excludeClientId) {
                ClientInfo client = clients.get(clientId);
                sb.append("Client #").append(clientId)
                        .append(" (").append(client.getUsername()).append(")")
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }

    public void setCurrentDir(int clientId, String dir) {
        ClientInfo client = clients.get(clientId);
        if (client != null) {
            client.setCurrentDir(dir);
        }
    }

    public String getCurrentDir(int clientId) {
        ClientInfo client = clients.get(clientId);
        return client != null ? client.getCurrentDir() : ".";
    }
}