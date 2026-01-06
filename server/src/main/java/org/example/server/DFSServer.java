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

public class DFSServer {
    private final ServerConfig config;
    private final ExecutorService threadPool;
    private final ClientManager clientManager;
    private final VirtualFileSystem vfs;
    private final DatabaseManager dbManager;

    public DFSServer(ServerConfig config) throws IOException, SQLException {
        this.config = config;
        this.threadPool = Executors.newCachedThreadPool();
        this.clientManager = new ClientManager();
        this.vfs = new VirtualFileSystem(config.getResourcesPath());
        this.dbManager = new DatabaseManager(config);
        this.dbManager.initialize();
    }

    public static void main(String[] args) {
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
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void start() {

        System.out.println("Starting Distributed File System Server on port " + config.getPort());
        System.out.println("Virtual File System root: " + config.getResourcesPath());

        try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
            System.out.println("Server ready. Waiting for connections...");

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());

                ClientSessionHandler handler = new ClientSessionHandler(
                        clientSocket, clientManager, vfs, dbManager
                );

                threadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void shutdown() {

        System.out.println("Shutting down server...");
        threadPool.shutdown();
        System.out.println("Total clients served: " + clientManager.getClientCount());
    }
}
