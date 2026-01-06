package org.example.server;

import org.example.client.ClientInfo;
import org.example.client.ClientManager;
import org.example.db.DatabaseManager;
import org.example.db.User;
import org.example.vfs.FileEntry;
import org.example.vfs.VirtualFileSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Handles a client session for the distributed file system server.
 * Uses modern Java features and best practices.
 */
public class ClientSessionHandler implements Runnable {
    private final Socket clientSocket;
    private final ClientManager clientManager;
    private final VirtualFileSystem vfs;
    private final DatabaseManager dbManager;
    private ClientInfo clientInfo;

    private BufferedReader input;
    private PrintWriter output;

    // Constants for error messages
    private static final String ERR_INVALID_FORMAT = "ERROR: Invalid format. Use: login <user> <pass>, signup <user> <pass> OR guest";
    private static final String ERR_UNKNOWN_CMD = "ERROR: Unknown command. Use login or signup.";
    private static final String ERR_DB = "ERROR: Database error: ";
    private static final String ERR_DUPLICATE_USER = "ERROR: Username already exists";
    private static final String ERR_INVALID_USER_PASS = "ERROR: Invalid username or password";
    private static final String SUCCESS_REGISTER = "SUCCESS: User registered. Please login.";
    private static final String SUCCESS_LOGIN = "SUCCESS: Welcome back, %s [%s]";
    private static final String SUCCESS_GUEST = "SUCCESS: Logged in as GUEST";
    private static final String HELP_PROMPT = "Type 'help' for available commands";

    public ClientSessionHandler(Socket clientSocket, ClientManager clientManager, VirtualFileSystem vfs, DatabaseManager dbManager) {
        this.clientSocket = clientSocket;
        this.clientManager = clientManager;
        this.vfs = vfs;
        this.dbManager = dbManager;
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
        output.println("Available: login <user> <pass> OR signup <user> <pass> OR guest");
        while (true) {
            String line = input.readLine();
            if (line == null) return false;
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;
            
            if (trimmedLine.equalsIgnoreCase("guest")) {
                clientInfo = clientManager.registerClient("guest_" + (int)(Math.random() * 10000), "guest");
                output.println(SUCCESS_GUEST);
                output.println(HELP_PROMPT);
                return true;
            }
            
            String[] parts = trimmedLine.split("\\s+", 3);
            if (parts.length < 3) {
                output.println(ERR_INVALID_FORMAT);
                continue;
            }
            
            String cmd = parts[0].toLowerCase();
            String username = parts[1].trim();
            String password = parts[2].trim();
            
            try {
                if (cmd.equals("login")) {
                    User user = dbManager.authenticate(username, password);
                    if (user != null) {
                        clientInfo = clientManager.registerClient(user.username(), user.role());
                        output.println(String.format(SUCCESS_LOGIN, user.username(), user.role()));
                        output.println(HELP_PROMPT);
                        return true;
                    } else {
                        output.println(ERR_INVALID_USER_PASS);
                    }
                } else if (cmd.equals("signup")) {
                    if (dbManager.registerUser(username, password, "guest")) {
                        output.println(SUCCESS_REGISTER);
                        return false;
                    } else {
                        output.println(ERR_DUPLICATE_USER);
                    }
                } else {
                    output.println(ERR_UNKNOWN_CMD);
                }
            } catch (SQLException e) {
                output.println(ERR_DB + e.getMessage());
            }
        }
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

    private String executeAdminCommands(String cmd, String args) {
        try {
            switch (cmd) {
                case "listroles":
                    List<String> roles = dbManager.getAllRoles();
                    return String.join(", ", roles);
                case "createrole":
                    // args: roleName cmd1,cmd2,...
                     String[] parts = args.split("\\s+", 2);
                    if (parts.length != 2) return "ERROR: Usage: createrole <role> <comma-separated-commands>";
                    dbManager.createOrUpdateRole(parts[0], parts[1]);
                    return "SUCCESS: Role created/updated";
                case "deleterole":
                    if (args.isEmpty()) return "ERROR: Usage: deleterole <role>";
                    dbManager.deleteRole(args.trim());
                    return "SUCCESS: Role deleted";
                case "shutdown":
                    // only admin allowed and intention is to stop the server
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        System.exit(0);
                    }).start();
                    return "SUCCESS: Server shutting down";
                default:
                    return "ERROR: Unknown admin command";
            }
        } catch (SQLException e) {
            return "ERROR: DB error: " + e.getMessage();
        }
    }

    private String executeCommand(String command) {
        if (command.isEmpty()) {
            return "ERROR: Empty command";
        }

        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        // Permission check via DB
        try {
            if (dbManager.isCommandForbidden(clientInfo.role(), cmd)) {
                return "ERROR: Permission denied. Your role does not allow this command.";
            }
        } catch (SQLException e) {
            return "ERROR: Permission check failed: " + e.getMessage();
        }

        try {
            // Admin commands handled here
            if (List.of("listroles","createrole","deleterole","shutdown").contains(cmd)) {
                if (dbManager.isCommandForbidden(clientInfo.role(), cmd)) {
                    return "ERROR: Permission denied. Admin only.";
                }
                return executeAdminCommands(cmd, args);
            }

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
                    return "Current directory: " + clientInfo.currentDirectory();
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
                    return "You are client #" + clientInfo.clientId() +
                            " (" + clientInfo.username() + ")";
                }
                case "help" -> {
                    return getHelpText();
                }
                case "kick" -> {
                    if (dbManager.isCommandForbidden(clientInfo.role(), "kick")) {
                        return "ERROR: Permission denied. Admin only.";
                    }
                    if (args.isEmpty()) return "ERROR: Usage: kick <client-id>";
                    try {
                        int targetId = Integer.parseInt(args);
                        clientManager.unregisterClient(targetId);
                        return "SUCCESS: Client #" + targetId + " kicked.";
                    } catch (NumberFormatException e) {
                        return "ERROR: Invalid client ID";
                    }
                }
                case "setrole" -> {
                    if (dbManager.isCommandForbidden(clientInfo.role(), "setrole")) {
                        return "ERROR: Permission denied. Admin only.";
                    }
                    String[] roleArgs = args.split("\\s+", 2);
                    if (roleArgs.length != 2) return "ERROR: Usage: setrole <username> <role>";
                    try {
                        if (dbManager.updateUserRole(roleArgs[0], roleArgs[1])) {
                            return "SUCCESS: Role updated for " + roleArgs[0];
                        } else {
                            return "ERROR: User not found";
                        }
                    } catch (SQLException e) {
                        return "ERROR: Database error: " + e.getMessage();
                    }
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
        clientInfo = clientInfo.withCurrentDirectory(args);
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


        remotePath = normalizePath(remotePath);


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
        FileEntry entry = vfs.createFile(
                remotePath,
                fileData,
                clientInfo.username(),
                clientInfo.clientId()
        );

        return "SUCCESS: File uploaded: " + entry;
    }

    private String handleDownload(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: download <remote-file-path>";
        }

        String remotePath = args;

        remotePath = normalizePath(remotePath);

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
        String directory = args.isEmpty() ? clientInfo.currentDirectory() : args;

        if (directory.startsWith("/")) {
            directory = directory.substring(1);
        }
        if (!directory.isEmpty() && !directory.endsWith("/")) {
            directory += "/";
        }
        if (directory.equals("/")) directory = "";

        List<FileEntry> files = vfs.listFiles(directory);

        if (files.isEmpty()) {
            return "No files found in " + (directory.isEmpty() ? "root" : directory);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Files in ").append(directory.isEmpty() ? "root" : directory).append(":\n");
        for (FileEntry file : files) {
            sb.append("  ").append(file.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    private String handleMyFiles() {
        List<FileEntry> files = vfs.getClientFiles(clientInfo.clientId());

        if (files.isEmpty()) {
            return "You don't have any files.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Your files:\n");
        for (FileEntry file : files) {
            sb.append("  ").append(file.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    private String handleDelete(String args) {
        if (args.isEmpty()) {
            return "ERROR: Usage: delete <file-path>";
        }

        String remotePath = args;

        remotePath = normalizePath(remotePath);

        try {
            boolean deleted = vfs.deleteFile(remotePath, clientInfo.clientId());
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

        remotePath = normalizePath(remotePath);

        try {
            FileEntry entry = vfs.createFile(
                    remotePath,
                    null,
                    clientInfo.username(),
                    clientInfo.clientId(),
                    "directory"
            );
            return "SUCCESS: Directory created: " + entry.path();
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String getHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Commands:\n");
        sb.append("==================\n");

        try {
            String commands = dbManager.getRoleCommands(clientInfo.role());
            if (commands == null) {
                sb.append("(No commands configured for your role)\n");
                return sb.toString();
            }
            if (commands.trim().equals("*")) {
                sb.append("All commands available (admin)\n");
                // Show general examples
                sb.append("Examples:\n");
                sb.append("  upload /home/user/file.txt\n");
                sb.append("  download file.txt\n");
                sb.append("  list .\n");
                sb.append("  delete oldfile.txt\n");
                return sb.toString();
            }

            List<String> list = Arrays.stream(commands.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            for (String c : list) {
                sb.append("  ").append(c).append("\n");
            }

            sb.append("\nExamples:\n");
            sb.append("  upload /home/user/file.txt\n");
            sb.append("  download file.txt\n");
            sb.append("  list .\n");
            sb.append("  delete oldfile.txt\n");

        } catch (SQLException e) {
            sb.append("ERROR: Unable to load help: ").append(e.getMessage());
        }

        return sb.toString();
    }

    /**
     * Normalizes a remote path relative to current directory.
     * @param remotePath the path to normalize
     * @return normalized absolute path without leading slash
     */
    private String normalizePath(String remotePath) {
        if (!remotePath.startsWith("/")) {
            String currentDir = clientInfo.currentDirectory();
            if (!currentDir.endsWith("/")) currentDir += "/";
            remotePath = currentDir + remotePath;
        }

        if (remotePath.startsWith("/")) {
            remotePath = remotePath.substring(1);
        }

        return remotePath;
    }

    private void cleanup() {
        try {
            if (clientInfo != null) {
                clientManager.unregisterClient(clientInfo.clientId());
                System.out.println("Client #" + clientInfo.clientId() + " disconnected");
            }
            if (input != null) input.close();
            if (output != null) output.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.print("Error during cleanup: ");
            System.err.println(e.getMessage());
        }
    }
}
