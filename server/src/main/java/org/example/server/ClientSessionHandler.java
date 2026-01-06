package org.example.server;

import org.example.client.ClientInfo;
import org.example.client.ClientManager;
import org.example.vfs.VirtualFileSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

public class ClientSessionHandler implements Runnable {
    private final Socket clientSocket;
    private final ClientManager clientManager;
    private final VirtualFileSystem vfs;
    private ClientInfo clientInfo;

    private BufferedReader input;
    private PrintWriter output;

    public ClientSessionHandler(Socket clientSocket, ClientManager clientManager, VirtualFileSystem vfs) {
        this.clientSocket = clientSocket;
        this.clientManager = clientManager;
        this.vfs = vfs;
    }

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);


            if (!authenticate()) {
                return;
            }


            processCommands();

        } catch (IOException e) {
            System.err.println("Client session error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private boolean authenticate() throws IOException {
        output.println("=== Distributed File System ===");
        output.println("Please authenticate (username,password):");

        String authLine = input.readLine();
        if (authLine == null) {
            return false;
        }


        String[] parts = authLine.split(",", 2);
        if (parts.length != 2) {
            output.println("ERROR: Invalid format. Use: username,password");
            return false;
        }

        String username = parts[0].trim();
        String password = parts[1].trim();


        if (username.isEmpty() || password.isEmpty()) {
            output.println("ERROR: Invalid credentials");
            return false;
        }


        clientInfo = clientManager.registerClient(username);
        output.println("SUCCESS: Authenticated as client #" + clientInfo.getClientId());
        output.println("Type 'help' for available commands");

        return true;
    }

    private void processCommands() throws IOException {
        String command;
        while ((command = input.readLine()) != null) {
            String response = executeCommand(command.trim());
            output.println(response);
            output.println("CMD_END");

            if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                break;
            }
        }
    }

    private String executeCommand(String command) {
        if (command.isEmpty()) {
            return "ERROR: Empty command";
        }

        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        try {
            switch (cmd) {
                case "upload" -> {
                    return handleUpload(args);
                }
                case "download" -> {
                    return handleDownload(args);
                }
                case "list" -> {
                    return handleList(args);
                }
                case "myfiles" -> {
                    return handleMyFiles();
                }
                case "pwd" -> {
                    return "Current directory: " + clientInfo.getCurrentDirectory();
                }
                case "cd" -> {
                    return handleChangeDirectory(args);
                }
                case "delete" -> {
                    return handleDelete(args);
                }
                case "mkdir" -> {
                    return handleMkdir(args);
                }
                case "whoami" -> {
                    return "You are client #" + clientInfo.getClientId() +
                            " (" + clientInfo.getUsername() + ")";
                }
                case "help" -> {
                    return getHelpText();
                }
                case "refresh" -> {
                    vfs.loadExistingFiles();
                    return "SUCCESS: File system metadata reloaded";
                }
                case "quit", "exit" -> {
                    return "Goodbye!";
                }
                case "listclients" -> {
                    return clientManager.listConnectedClients();
                }
                case "clientinfo" -> {
                    if (args.isEmpty()) {
                        return "ERROR: Usage: clientinfo <client-id>";
                    }
                    try {
                        int clientId = Integer.parseInt(args);
                        ClientInfo info = clientManager.getClient(clientId);
                        return info != null ? info.toString() : "ERROR: Client not found";
                    } catch (NumberFormatException e) {
                        return "ERROR: Invalid client ID";
                    }
                }
                default -> {
                    return "ERROR: Unknown command. Type 'help' for available commands.";
                }
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleChangeDirectory(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: cd <directory>";
        }
        clientInfo.setCurrentDirectory(args);
        return "SUCCESS: Changed directory to: " + args;
    }

    private String handleUpload(String args) throws IOException {
        if (args.isEmpty()) {
            return "ERROR: Usage: upload <local-file-path> [remote-path]";
        }

        String[] uploadArgs = args.split("\\s+", 2);
        String localPath = uploadArgs[0];
        String remotePath = uploadArgs.length > 1 ? uploadArgs[1] :
                Paths.get(localPath).getFileName().toString();


        if (!remotePath.startsWith("/")) {
            String currentDir = clientInfo.getCurrentDirectory();
            if (!currentDir.endsWith("/")) currentDir += "/";
            remotePath = currentDir + remotePath;
        }


        if (remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }


        output.println("READY: Send file data (base64 encoded). END with 'EOF' on a new line.");

        StringBuilder base64Data = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.equals("EOF")) {
                break;
            }
            base64Data.append(line);
        }

        if (base64Data.isEmpty()) {
            return "ERROR: No file data received";
        }


        byte[] fileData = Base64.getDecoder().decode(base64Data.toString());
        VirtualFileSystem.FileEntry entry = vfs.createFile(
                remotePath,
                fileData,
                clientInfo.getUsername(),
                clientInfo.getClientId()
        );

        return "SUCCESS: File uploaded: " + entry;
    }

    private String handleDownload(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: download <remote-file-path>";
        }

        String remotePath = args;

        if (!remotePath.startsWith("/")) {
            String currentDir = clientInfo.getCurrentDirectory();
            if (!currentDir.endsWith("/")) currentDir += "/";
            remotePath = currentDir + remotePath;
        }


        if (remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }

        try {
            byte[] fileData = vfs.readFile(remotePath);
            String base64Data = Base64.getEncoder().encodeToString(fileData);


            output.println("FILE_DATA:");
            output.println(base64Data);
            output.println("END_FILE");

            return "SUCCESS: File sent";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleList(String args) {
        String directory = args.isEmpty() ? clientInfo.getCurrentDirectory() : args;

        if (directory.startsWith("/")) {
            directory = directory.substring(1);
        }
        if (!directory.isEmpty() && !directory.endsWith("/")) {
            directory += "/";
        }
        if (directory.equals("/")) directory = "";

        List<VirtualFileSystem.FileEntry> files = vfs.listFiles(directory);

        if (files.isEmpty()) {
            return "No files found in " + (directory.isEmpty() ? "root" : directory);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Files in ").append(directory.isEmpty() ? "root" : directory).append(":\n");
        for (VirtualFileSystem.FileEntry file : files) {
            sb.append("  ").append(file.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    private String handleMyFiles() {
        List<VirtualFileSystem.FileEntry> files = vfs.getClientFiles(clientInfo.getClientId());

        if (files.isEmpty()) {
            return "You don't have any files.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Your files:\n");
        for (VirtualFileSystem.FileEntry file : files) {
            sb.append("  ").append(file.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    private String handleDelete(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: delete <file-path>";
        }

        String remotePath = args;

        if (!remotePath.startsWith("/")) {
            String currentDir = clientInfo.getCurrentDirectory();
            if (!currentDir.endsWith("/")) currentDir += "/";
            remotePath = currentDir + remotePath;
        }


        if (remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }

        try {
            boolean deleted = vfs.deleteFile(remotePath, clientInfo.getClientId());
            return deleted ? "SUCCESS: File deleted" : "ERROR: File not found";
        } catch (SecurityException e) {
            return "ERROR: Permission denied";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleMkdir(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: mkdir <directory-name>";
        }

        String remotePath = args;

        if (!remotePath.startsWith("/")) {
            String currentDir = clientInfo.getCurrentDirectory();
            if (!currentDir.endsWith("/")) currentDir += "/";
            remotePath = currentDir + remotePath;
        }


        if (remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }

        try {
            VirtualFileSystem.FileEntry entry = vfs.createFile(
                    remotePath,
                    null,
                    clientInfo.getUsername(),
                    clientInfo.getClientId(),
                    "directory"
            );
            return "SUCCESS: Directory created: " + entry.getPath();
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String getHelpText() {
        return """
                Available Commands:
                ==================
                upload <local> [remote]  - Upload a file
                download <remote>        - Download a file
                list [directory]         - List files
                myfiles                  - List your files
                pwd                      - Show current directory
                cd <directory>           - Change current directory
                delete <file>            - Delete a file
                mkdir <directory>        - Create directory
                whoami                   - Show current user
                help                     - Show this help
                refresh                  - Reload file system metadata
                listclients              - List all connected clients
                clientinfo <id>          - Show information about a client
                quit/exit                - Disconnect
                
                Examples:
                  upload /home/user/file.txt
                  download file.txt
                  list .
                  delete oldfile.txt
                """;
    }

    private void cleanup() {
        try {
            if (clientInfo != null) {
                clientManager.unregisterClient(clientInfo.getClientId());
                System.out.println("Client #" + clientInfo.getClientId() + " disconnected");
            }
            if (input != null) input.close();
            if (output != null) output.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}
