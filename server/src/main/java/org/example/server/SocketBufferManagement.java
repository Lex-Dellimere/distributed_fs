package org.example.server;

import org.example.client.ClientManager;
import org.example.file.FileManager;

import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.List;

public class SocketBufferManagement {
    private final Socket clientConnection;
    private final PrintWriter output;
    private final BufferedReader input;
    private final ClientManager clientManager;
    private final FileManager fileManager;
    private int clientId = -1;
    private String username;

    public SocketBufferManagement(Socket clientSocket, ClientManager clientManager, FileManager fileManager) throws IOException {
        this.clientConnection = clientSocket;
        this.output = new PrintWriter(clientConnection.getOutputStream(), true);
        this.input = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
        this.clientManager = clientManager;
        this.fileManager = fileManager;
    }

    private void logEvent(String event) {
        System.out.println("[Client #" + (clientId > 0 ? clientId : "?") + "] " + event);
    }

    public void handleClient() throws IOException {
        try {
            String authLine = input.readLine();
            if (authLine == null) {
                return;
            }

            logEvent("Authentication attempt: " + authLine);

            if (authLine.contains(",")) {
                String[] parts = authLine.split(",", 2);
                String user = parts[0].trim();
                String pass = parts[1].trim();

                if (!user.isEmpty() && !pass.isEmpty()) {
                    var clientInfo = clientManager.registerClient(user);
                    this.clientId = clientInfo.getClientId();
                    this.username = user;

                    output.println("CLIENT_ID:" + clientId);
                    logEvent("Assigned Client ID: " + clientId + " to user: " + username);

                    processCommands();
                } else {
                    output.println("AUTH_FAILED: Invalid credentials");
                }
            } else {
                output.println("AUTH_FAILED: Expected username,password format");
            }
        } finally {
            if (clientId > 0) {
                clientManager.unregisterClient(clientId);
                logEvent("Client disconnected");
            }
            closeResources();
        }
    }

    private void processCommands() throws IOException {
        String command;
        while ((command = input.readLine()) != null) {
            logEvent("Command received: " + command);

            if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                output.println("Goodbye!");
                break;
            }

            String response = processCommand(command);
            output.println(response);
        }
    }

    private String processCommand(String command) {
        try {
            if (command.startsWith("server ")) {
                String cmd = command.substring(7).trim();

                if (cmd.equalsIgnoreCase("list-others")) {
                    return clientManager.listOtherClients(clientId);

                } else if (cmd.equalsIgnoreCase("list-local")) {
                    String currentDir = clientManager.getCurrentDir(clientId);
                    List<String> files = fileManager.listFiles(currentDir);

                    if (files.isEmpty()) {
                        return "Directory is empty";
                    }

                    StringBuilder result = new StringBuilder();
                    result.append("Current directory: ").append(currentDir).append("\n");
                    for (String file : files) {
                        result.append(file).append("\n");
                    }
                    return result.toString().trim();

                } else if (cmd.startsWith("get ")) {
                    String fileName = cmd.substring(4).trim();
                    String currentDir = clientManager.getCurrentDir(clientId);
                    String fullPath = currentDir.equals(".") ? fileName : currentDir + "/" + fileName;

                    try {
                        byte[] fileData = fileManager.getFile(fullPath);
                        String base64Data = Base64.getEncoder().encodeToString(fileData);
                        return "FILE_DATA:" + base64Data;
                    } catch (IOException e) {
                        return "ERROR: File not found or cannot be read: " + e.getMessage();
                    }

                } else if (cmd.startsWith("cd ")) {
                    String dir = cmd.substring(3).trim();
                    String currentDir = clientManager.getCurrentDir(clientId);

                    if (dir.equals("..")) {
                        if (!currentDir.equals(".")) {
                            int lastSlash = currentDir.lastIndexOf('/');
                            if (lastSlash == -1) {
                                clientManager.setCurrentDir(clientId, ".");
                            } else {
                                clientManager.setCurrentDir(clientId, currentDir.substring(0, lastSlash));
                            }
                        }
                    } else if (dir.equals(".") || dir.equals("/")) {
                        clientManager.setCurrentDir(clientId, ".");
                    } else {
                        String newDir;
                        if (currentDir.equals(".")) {
                            newDir = dir;
                        } else {
                            newDir = currentDir + "/" + dir;
                        }

                        try {
                            fileManager.listFiles(newDir);
                            clientManager.setCurrentDir(clientId, newDir);
                        } catch (IOException e) {
                            return "ERROR: Directory not found: " + dir;
                        }
                    }

                    return "Changed directory to: " + clientManager.getCurrentDir(clientId);

                } else if (cmd.startsWith("mkdir ")) {
                    String dirName = cmd.substring(6).trim();
                    String currentDir = clientManager.getCurrentDir(clientId);
                    String fullPath = currentDir.equals(".") ? dirName : currentDir + "/" + dirName;

                    if (fileManager.createDirectory(fullPath, clientId, username)) {
                        return "Directory created: " + dirName;
                    } else {
                        return "ERROR: Directory already exists or cannot be created";
                    }

                } else if (cmd.startsWith("put ")) {
                    // Get file name
                    String fileName = cmd.substring(4).trim();

                    // Read file data (base64 encoded)
                    String fileDataLine = input.readLine();
                    if (fileDataLine == null || !fileDataLine.startsWith("FILE_DATA:")) {
                        return "ERROR: Expected file data";
                    }

                    String base64Data = fileDataLine.substring(10);
                    byte[] fileData = Base64.getDecoder().decode(base64Data);

                    String currentDir = clientManager.getCurrentDir(clientId);
                    String fullPath = currentDir.equals(".") ? fileName : currentDir + "/" + fileName;

                    if (fileManager.putFile(fullPath, fileData, clientId, username)) {
                        return "File uploaded: " + fileName + " (" + fileData.length + " bytes)";
                    } else {
                        return "ERROR: Failed to upload file";
                    }

                } else if (cmd.startsWith("delete ")) {
                    String target = cmd.substring(7).trim();
                    String currentDir = clientManager.getCurrentDir(clientId);
                    String fullPath = currentDir.equals(".") ? target : currentDir + "/" + target;

                    if (fileManager.delete(fullPath)) {
                        return "Deleted: " + target;
                    } else {
                        return "ERROR: File/directory not found";
                    }

                } else if (cmd.equalsIgnoreCase("pwd")) {
                    return "Current directory: " + clientManager.getCurrentDir(clientId);

                } else {
                    return "ERROR: Unknown command. Available commands: list-others, list-local, get <file>, cd <dir>, mkdir <dir>, put <file>, delete <file>, pwd";
                }
            } else {
                return "ERROR: Commands must start with 'server'";
            }
        } catch (Exception e) {
            logEvent("Error processing command: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private void closeResources() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (clientConnection != null) clientConnection.close();
        } catch (IOException e) {
            logEvent("Error closing resources: " + e.getMessage());
        }
    }
}