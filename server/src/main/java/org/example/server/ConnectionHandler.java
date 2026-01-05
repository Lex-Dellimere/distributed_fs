package org.example.server;

import org.example.client.ClientManager;
import org.example.file.FileManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionHandler {
    private final ServerSocket ss;
    private final ExecutorService executor;
    private final ClientManager clientManager;
    private final FileManager fileManager;

    public ConnectionHandler(int portBind) {
        this.clientManager = new ClientManager();

        try {
            this.fileManager = new FileManager();
            this.ss = new ServerSocket(portBind);
            this.executor = Executors.newFixedThreadPool(10);
            System.out.println("Server started on port " + portBind);
            System.out.println("Server root: " + fileManager.getServerRoot());
        } catch (IOException e) {
            System.err.println("FATAL: Failed to initialize server: " + e.getMessage());
            System.err.println("Make sure port " + portBind + " is available.");
            System.exit(1);
            throw new IllegalStateException("Server initialization failed", e);
        }
    }

    public void runServer() {
        System.out.println("Server ready. Waiting for connections...");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = ss.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                executor.submit(() -> {
                    try {
                        var sbm = new SocketBufferManagement(clientSocket, clientManager, fileManager);
                        sbm.handleClient();
                    } catch (IOException e) {
                        System.err.println("Error handling client: " + e.getMessage());
                        if (!e.getMessage().contains("Connection reset") &&
                                !e.getMessage().contains("Socket closed")) {
                            System.err.println("Stack trace:");
                            for (StackTraceElement element : e.getStackTrace()) {
                                System.err.println("    at " + element);
                            }
                        }
                    }
                });
            } catch (IOException e) {
                if (!ss.isClosed()) {
                    System.err.println("Error accepting client: " + e.getMessage());
                }
                break;
            }
        }
    }
}