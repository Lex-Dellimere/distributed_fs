package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Scanner;

public class REPL {
    private final Connection connection;
    private final Scanner scanner;
    private boolean running = true;

    public REPL(Connection connection, Scanner scanner) {
        this.connection = connection;
        this.scanner = scanner;
    }

    public void run() {
        System.out.println("\n=== REPL Started ===");
        System.out.println("Type 'help' for commands\n");

        while (running && connection.isConnected()) {
            try {
                showPrompt();

                if (!scanner.hasNextLine()) {
                    Thread.sleep(100);
                    continue;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                handleCommand(line);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.out.println("\nError: " + e.getMessage());
            }
        }

        if (!connection.isConnected()) {
            System.out.println("\nDisconnected from server.");
        }
    }

    private void showPrompt() {
        if (connection.isAuthenticated()) {
            System.out.print(connection.getUsername() + "#" + connection.getClientId() + "> ");
        } else {
            System.out.print("NOT_AUTH> ");
        }
        System.out.flush();
    }

    private void handleCommand(String line) throws IOException, InterruptedException {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "quit":
            case "exit":
                running = false;
                connection.sendCommand("quit");
                System.out.println("Exiting...");
                return;

            case "help":
                showHelp();
                return;

            case "status":
                showStatus();
                return;

            case "clear":
                clearScreen();
                return;

            case "reconnect":
                handleReconnect();
                return;

            case "upload":
                handleUpload(args);
                return;

            case "download":
                handleDownload(args);
                return;

            case "list":
                handleList(args);
                return;

            case "delete":
                handleDelete(args);
                return;

            case "mkdir":
                handleMkdir(args);
                return;

            case "whoami":
                handleWhoami();
                return;

            case "pwd":
            case "cd":
            case "listclients":
            case "clientinfo":
            case "refresh":
            case "myfiles":
                handleGenericCommand(line);
                return;

            default:

                handleGenericCommand(line);
        }
    }

    private void handleUpload(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: upload <local-file-path> [remote-name]");
            return;
        }

        String[] uploadArgs = args.split("\\s+", 2);
        String localPath = uploadArgs[0];
        String remotePath = uploadArgs.length > 1 ? uploadArgs[1] :
                Path.of(localPath).getFileName().toString();

        try {

            Path filePath = Path.of(localPath);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                System.out.println("ERROR: File not found: " + localPath);
                return;
            }


            System.out.print("Reading file... ");
            byte[] fileData = Files.readAllBytes(filePath);
            System.out.println("Read " + fileData.length + " bytes");


            String base64Data = Base64.getEncoder().encodeToString(fileData);


            String command = "upload " + localPath + " " + remotePath;
            System.out.println("Sending upload command...");

            String response = connection.sendAndWaitForResponse(command, 10000);

            if (response == null) {
                System.out.println("ERROR: No response from server");
                return;
            }

            if (response.startsWith("READY:")) {
                System.out.println(response.substring(6));
                System.out.print("Sending file data... ");


                connection.sendCommand(base64Data);

                connection.sendCommand("EOF");

                System.out.println("Sent");


                String finalResponse = connection.sendAndWaitForResponse("", 10000);
                if (finalResponse != null) {
                    if (finalResponse.startsWith("SUCCESS:")) {
                        System.out.println("SUCCESS: " + finalResponse.substring(8));
                    } else if (finalResponse.startsWith("ERROR:")) {
                        System.out.println("ERROR: " + finalResponse.substring(6));
                    } else {
                        System.out.println("Response: " + finalResponse);
                    }
                } else {
                    System.out.println("ERROR: No final response from server");
                }

            } else if (response.startsWith("ERROR:")) {
                System.out.println("ERROR: " + response.substring(6));
            } else {
                System.out.println("Unexpected response: " + response);
            }

        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("Upload failed: " + e.getMessage());
        }
    }

    private void handleDownload(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: download <remote-file-path>");
            return;
        }

        try {
            String response = connection.sendAndWaitForResponse("download " + args, 10000);

            if (response == null) {
                System.out.println("ERROR: No response from server");
                return;
            }

            if (response.startsWith("FILE_DATA:")) {
                String base64Data = response.substring(10);
                byte[] fileData = Base64.getDecoder().decode(base64Data);


                String fileName = Path.of(args).getFileName().toString();
                Files.write(Path.of(fileName), fileData);
                System.out.println("File downloaded as: " + fileName + " (" + fileData.length + " bytes)");
            } else if (response.startsWith("ERROR:")) {
                System.out.println("ERROR: " + response.substring(6));
            } else {
                System.out.println("Unexpected response: " + response);
            }

        } catch (Exception e) {
            System.out.println("Download failed: " + e.getMessage());
        }
    }

    private void handleList(String args) {
        try {
            String command = args.isEmpty() ? "list" : "list " + args;
            String response = connection.sendAndWaitForResponse(command, 5000);

            if (response != null) {
                if (response.startsWith("ERROR:")) {
                    System.out.println("ERROR: " + response.substring(6));
                } else {
                    System.out.println(response);
                }
            } else {
                System.out.println("No response from server");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleDelete(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: delete <file-path>");
            return;
        }

        try {
            String response = connection.sendAndWaitForResponse("delete " + args, 5000);
            handleServerResponse(response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleMkdir(String args) {
        if (args.isEmpty()) {
            System.out.println("Usage: mkdir <directory-name>");
            return;
        }

        try {
            String response = connection.sendAndWaitForResponse("mkdir " + args, 5000);
            handleServerResponse(response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleWhoami() {
        try {
            String response = connection.sendAndWaitForResponse("whoami", 5000);
            handleServerResponse(response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleGenericCommand(String command) {
        try {
            String response = connection.sendAndWaitForResponse(command, 5000);
            handleServerResponse(response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleServerResponse(String response) {
        if (response == null) {
            System.out.println("No response from server");
        } else if (response.startsWith("ERROR:")) {
            System.out.println("ERROR: " + response.substring(6));
        } else if (response.startsWith("SUCCESS:")) {
            System.out.println("SUCCESS: " + response.substring(8));
        } else {
            System.out.println(response);
        }
    }

    private void handleReconnect() {
        System.out.println("Reconnecting...");
        connection.stop();

        try {
            Thread.sleep(1000);
            connection.start();
            Thread.sleep(1000);

            if (connection.isConnected()) {
                System.out.println("Reconnected successfully");
            } else {
                System.out.println("Reconnection failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void showHelp() {
        try {
            String response = connection.sendAndWaitForResponse("help", 5000);
            if (response != null) {
                System.out.println(response);
            } else {
                System.out.println("ERROR: Could not fetch help from server");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("\n=== Local Commands ===");
        System.out.println("  status               - Show connection status");
        System.out.println("  clear                - Clear screen");
        System.out.println("  reconnect            - Reconnect to server");
        System.out.println("  quit/exit            - Exit client");
        System.out.println("================\n");
    }

    private void showStatus() {
        System.out.println("\n=== Status ===");
        System.out.println("Connected:    " + (connection.isConnected() ? "YES" : "NO"));
        System.out.println("Authenticated:" + (connection.isAuthenticated() ? "YES" : "NO"));
        System.out.println("Client ID:    " + connection.getClientId());
        System.out.println("Username:     " + connection.getUsername());
        System.out.println("==============\n");
    }

    private void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }
}
