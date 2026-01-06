package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CommandProcessor {
    private final Connection connection;
    private final Consumer<String> output;
    private final BiConsumer<String, String> notification;

    public CommandProcessor(Connection connection, Consumer<String> output, BiConsumer<String, String> notification) {
        this.connection = connection;
        this.output = output;
        this.notification = notification;
    }

    public void process(String line) {
        if (line == null || line.trim().isEmpty()) return;
        line = line.trim();

        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        try {
            switch (command) {
                case "help":
                    showHelp();
                    break;
                case "status":
                    showStatus();
                    break;
                case "upload":
                    handleUpload(args);
                    break;
                case "download":
                    handleDownload(args);
                    break;
                case "list":
                    handleList(args);
                    break;
                case "delete":
                    handleDelete(args);
                    break;
                case "mkdir":
                    handleMkdir(args);
                    break;
                case "whoami":
                    handleWhoami();
                    break;
                default:
                    handleGenericCommand(line);
                    break;
            }
        } catch (Exception e) {
            output.accept("Error: " + e.getMessage());
        }
    }

    private void handleUpload(String args) throws IOException, InterruptedException {
        if (args.isEmpty()) {
            notification.accept("Usage: upload <local-file-path> [remote-name]", "warning");
            output.accept("Usage: upload <local-file-path> [remote-name]");
            return;
        }

        String[] uploadArgs = args.split("\\s+", 2);
        String localPath = uploadArgs[0];
        String remotePath = uploadArgs.length > 1 ? uploadArgs[1] :
                Path.of(localPath).getFileName().toString();

        Path filePath = Path.of(localPath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            notification.accept("File not found: " + localPath, "error");
            output.accept("ERROR: File not found: " + localPath);
            return;
        }

        output.accept("Reading file " + localPath + "...");
        byte[] fileData = Files.readAllBytes(filePath);
        String base64Data = Base64.getEncoder().encodeToString(fileData);

        output.accept("Sending upload command...");
        String response = connection.sendAndWaitForResponse("upload " + localPath + " " + remotePath, 10000);

        if (response == null) {
            notification.accept("No response from server", "error");
            output.accept("ERROR: No response from server");
            return;
        }

        if (response.startsWith("READY:")) {
            output.accept("Server ready. Sending data...");
            connection.sendCommand(base64Data);
            connection.sendCommand("EOF");

            String finalResponse = connection.sendAndWaitForResponse("", 10000);
            handleServerResponse(finalResponse);
        } else {
            handleServerResponse(response);
        }
    }

    private void handleDownload(String args) {
        if (args.isEmpty()) {
            notification.accept("Usage: download <remote-file-path>", "warning");
            output.accept("Usage: download <remote-file-path>");
            return;
        }

        try {
            output.accept("Downloading " + args + "...");
            String response = connection.sendAndWaitForResponse("download " + args, 10000);

            if (response == null) {
                notification.accept("No response from server", "error");
                output.accept("ERROR: No response from server");
                return;
            }

            if (response.startsWith("FILE_DATA:")) {
                String base64Data = response.substring(10);
                byte[] fileData = Base64.getDecoder().decode(base64Data);
                String fileName = Path.of(args).getFileName().toString();
                Files.write(Path.of(fileName), fileData);
                notification.accept("File downloaded: " + fileName + " (" + fileData.length + " bytes)", "success");
                output.accept("SUCCESS: File downloaded as: " + fileName + " (" + fileData.length + " bytes)");
            } else {
                handleServerResponse(response);
            }
        } catch (Exception e) {
            notification.accept("Download failed: " + e.getMessage(), "error");
            output.accept("Download failed: " + e.getMessage());
        }
    }

    private void handleList(String args) throws IOException, InterruptedException {
        String command = args.isEmpty() ? "list" : "list " + args;
        String response = connection.sendAndWaitForResponse(command, 5000);
        handleServerResponse(response);
    }

    private void handleDelete(String args) throws IOException, InterruptedException {
        if (args.isEmpty()) {
            output.accept("Usage: delete <file-path>");
            return;
        }
        String response = connection.sendAndWaitForResponse("delete " + args, 5000);
        handleServerResponse(response);
    }

    private void handleMkdir(String args) throws IOException, InterruptedException {
        if (args.isEmpty()) {
            output.accept("Usage: mkdir <directory-name>");
            return;
        }
        String response = connection.sendAndWaitForResponse("mkdir " + args, 5000);
        handleServerResponse(response);
    }

    private void handleWhoami() throws IOException, InterruptedException {
        String response = connection.sendAndWaitForResponse("whoami", 5000);
        handleServerResponse(response);
    }

    private void handleGenericCommand(String command) throws IOException, InterruptedException {
        String response = connection.sendAndWaitForResponse(command, 5000);
        handleServerResponse(response);
    }

    private void handleServerResponse(String response) {
        if (response == null) {
            notification.accept("No response from server", "error");
            output.accept("No response from server");
        } else if (response.startsWith("ERROR: Permission denied")) {
            notification.accept("Permission denied - Your role (" + connection.getRole() + ") doesn't allow this command", "error");
            output.accept("ERROR: Permission denied - Your role (" + connection.getRole() + ") doesn't allow this command.");
            output.accept("Tip: Type 'help' to see available commands for your role.");
        } else if (response.startsWith("ERROR:")) {
            String errorMsg = response.substring(6).trim();
            notification.accept(errorMsg, "error");
            output.accept("ERROR: " + errorMsg);
        } else if (response.startsWith("SUCCESS:")) {
            String successMsg = response.substring(8).trim();
            notification.accept(successMsg, "success");
            output.accept("SUCCESS: " + successMsg);
        } else {
            output.accept(response);
        }
    }

    private void showHelp() throws IOException, InterruptedException {
        String response = connection.sendAndWaitForResponse("help", 5000);
        output.accept(Objects.requireNonNullElse(response, "ERROR: Could not fetch help from server"));
    }

    private void showStatus() {
        output.accept("\n=== Status ===");
        output.accept("Connected:    " + (connection.isConnected() ? "YES" : "NO"));
        output.accept("Authenticated:" + (connection.isAuthenticated() ? "YES" : "NO"));
        output.accept("Client ID:    " + connection.getClientId());
        output.accept("Username:     " + connection.getUsername());
        output.accept("Role:         " + connection.getRole());
        output.accept("==============\n");
    }
}
