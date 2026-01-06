package org.example.server;

import org.example.client.ClientManager;
import org.example.config.ServerConfig;
import org.example.db.DatabaseManager;
import org.example.vfs.VirtualFileSystem;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DFSServer {
    private final ServerConfig config;
    private final ExecutorService virtualThreadExecutor;
    private final ClientManager clientManager;
    private final VirtualFileSystem vfs;
    private final DatabaseManager dbManager;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;

    public DFSServer(ServerConfig config) throws IOException, SQLException {
        this.config = config;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.clientManager = new ClientManager();
        this.vfs = new VirtualFileSystem(config.getResourcesPath());
        this.dbManager = new DatabaseManager(config);
        this.running = new AtomicBoolean(false);

        virtualThreadExecutor.submit(() -> {
            try {
                dbManager.initialize();
                System.out.println("Database initialized successfully");
            } catch (SQLException e) {
                System.err.println("Database initialization failed: " + e.getMessage());
            }
        });
    }

    static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.load();
            if (args.length > 0) {
                config.setPort(Integer.parseInt(args[0]));
            }
            DFSServer server = new DFSServer(config);
            server.start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number");
            System.exit(1);
        } catch (IOException | SQLException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.err.println("Error details: " + e.getClass().getName());
            System.exit(1);
        }
    }

    public void start() {
        running.set(true);

        System.out.println("Starting Distributed File System Server on port " + config.getPort());
        System.out.println("Virtual File System root: " + config.getResourcesPath());
        System.out.println("Using virtual threads for concurrent operations");

        try {
            serverSocket = new ServerSocket(config.getPort());
            System.out.println("Server ready. Waiting for connections...");

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getInetAddress());

                    virtualThreadExecutor.submit(() -> {
                        ClientSessionHandler handler = new ClientSessionHandler(
                                clientSocket, clientManager, vfs, dbManager
                        );
                        handler.run();
                    });
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * Stops the server gracefully. Used by management GUI.
     */
    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }

    private void shutdown() {
        System.out.println("Shutting down server...");
        running.set(false);
        virtualThreadExecutor.shutdown();
        System.out.println("Total clients served: " + clientManager.getClientCount());
    }

    /**
     * Gets the client manager for monitoring. Used by management GUI.
     */
    public ClientManager getClientManager() {
        return clientManager;
    }

    /**
     * Checks if server is running. Used by management GUI.
     */
    public boolean isRunning() {
        return running.get();
    }
}
