package org.example.server;

import org.example.client.ClientManager;
import org.example.vfs.VirtualFileSystem;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DFSServer {
    private final int port;
    private final ExecutorService threadPool;
    private final ClientManager clientManager;
    private final VirtualFileSystem vfs;

    public DFSServer(int port) throws IOException {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.clientManager = new ClientManager();
        this.vfs = new VirtualFileSystem("dfs_root");
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 5555;
            DFSServer server = new DFSServer(port);
            server.start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() {

        System.out.println("Starting Distributed File System Server on port " + port);
        System.out.println("Virtual File System root: dfs_root");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server ready. Waiting for connections...");

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());

                ClientSessionHandler handler = new ClientSessionHandler(
                        clientSocket, clientManager, vfs
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
