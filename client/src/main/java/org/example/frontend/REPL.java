package org.example.frontend;

import org.example.communication.ConnectionHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Scanner;

public class REPL implements Runnable {
    private final ConnectionHandler connectionHandler;
    private final Scanner scanner = new Scanner(System.in);
    private volatile boolean running = true;

    public REPL(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            if (!connectionHandler.isConnected()) {
                System.out.println("Not connected to server. Waiting for connection...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (!connectionHandler.isAuthenticated()) {
                System.out.println("Connected but not authenticated. Waiting for authentication...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            System.out.print("CLIENT#" + connectionHandler.getClientId() + "> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            switch (line.toLowerCase()) {
                case "quit", "exit" -> {
                    System.out.println("Goodbye!");
                    stop();
                    return;
                }
                case "reconnect" -> {
                    System.out.println("Reconnecting... (connection handler will handle reconnection)");
                    continue;
                }
                case "status" -> {
                    displayStatus();
                    continue;
                }
                default -> handleCommand(line);
            }
        }
    }

    private void handleFileUpload(String fileName) {
        try {
            System.out.print("Enter path to local file: ");
            String localPath = scanner.nextLine().trim();

            Path filePath = Paths.get(localPath);
            if (!Files.exists(filePath)) {
                System.out.println("File not found: " + localPath);
                return;
            }

            byte[] fileData = Files.readAllBytes(filePath);
            String base64Data = Base64.getEncoder().encodeToString(fileData);

            String response = connectionHandler.sendCommandAndWait("Server put " + fileName, 0);

            if (response != null && !response.startsWith("ERROR")) {
                response = connectionHandler.sendCommandAndWait("FILE_DATA:" + base64Data, 0);
                System.out.println("Server Response: " + response);
            } else {
                System.out.println("Failed to initiate upload: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
        }
    }

    private void handleCommand(String line) {
        if (line.startsWith("server ")) {
            String command = line.substring(7).trim();
            if (command.startsWith("put ")) {
                handleFileUpload(command.substring(4).trim());
                return;
            }

            if (!command.isEmpty()) {
                try {
                    System.out.println("Sending command: " + command);

                    String response = connectionHandler.sendCommandAndWait(command, 5000);

                    if (response != null) {
                        System.out.println("Server Response: " + response);
                    } else {
                        System.out.println("No response received (timeout or error)");
                    }
                } catch (IOException e) {
                    System.err.println("Error sending command: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while waiting for response");
                }
            }
        } else {
            printHelp();
        }
    }

    private void displayStatus() {
        System.out.println("=== Status ===");
        System.out.println("Connected: " + connectionHandler.isConnected());
        System.out.println("Authenticated: " + connectionHandler.isAuthenticated());
        System.out.println("Client ID: " + connectionHandler.getClientId());
        System.out.println("==============");
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  server <command>  - Send command to server and wait for response");
        System.out.println("  status            - Show connection status");
        System.out.println("  reconnect         - Reconnect to server");
        System.out.println("  quit/exit         - Exit application");
        System.out.println();
    }

    public void stop() {
        running = false;
    }
}