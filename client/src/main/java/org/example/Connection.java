package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.*;

public class Connection {
    private String host;
    private int port;
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private volatile boolean authenticated = false;
    private volatile int clientId = -1;
    private volatile String username = "";
    private volatile String role = "Guest";

    public Connection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setConnectionInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void start() {
        executor.submit(this::connectionLoop);
        executor.submit(this::receiveLoop);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    private void connectionLoop() {
        while (running) {
            try {
                if (socket == null || socket.isClosed()) {
                    connect();
                }

                String command = sendQueue.poll(100, TimeUnit.MILLISECONDS);
                if (command != null && out != null) {
                    out.println(command);
                    System.out.println("[DEBUG] Sent: " + command);
                }

                Thread.sleep(10);

            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
                disconnect();
                sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void receiveLoop() {
        while (running) {
            try {
                if (in != null) {

                    if (in.ready()) {
                        String response = in.readLine();
                        if (response != null) {
                            System.out.println("[DEBUG] Received: " + response);
                            handleResponse(response);
                        }
                    }
                }
                Thread.sleep(10);
            } catch (IOException e) {
                System.err.println("Receive error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30000);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println("Connected to server");
    }

    private void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {

        }

        socket = null;
        out = null;
        in = null;
        authenticated = false;
        clientId = -1;
        username = "";
        System.out.println("\nDisconnected");
    }

    private void handleResponse(String response) {
        if (response == null) return;

        if (response.startsWith("SUCCESS: Authenticated as client #")) {
            try {
                String id = response.substring(response.indexOf("#") + 1).trim();
                clientId = Integer.parseInt(id);
                authenticated = true;
                responseQueue.offer("AUTH_SUCCESS:" + clientId);
                // Try to infer role if it's there? Usually server sends ROLE: after auth
                return;
            } catch (NumberFormatException e) {
                // Fall through to default handling
            }
        }

        if (response.startsWith("ROLE:")) {
            this.role = response.substring(5).trim();
            return;
        }

        if (response.equals("=== Distributed File System ===") || 
            response.equals("Please authenticate (username,password):") ||
            response.equals("Type 'help' for available commands")) {
            return;
        }

        if (response.startsWith("CLIENT_ID:")) {
            try {
                String id = response.substring(10).trim();
                clientId = Integer.parseInt(id);
                authenticated = true;
                responseQueue.offer("AUTH_SUCCESS:" + clientId);
                return;
            } catch (NumberFormatException e) {
                responseQueue.offer("ERROR: Invalid client ID format");
                return;
            }
        }

        if (response.startsWith("SUCCESS:")) {
            responseQueue.offer("SUCCESS:" + response.substring(8));
            return;
        }

        if (response.startsWith("ERROR:")) {
            responseQueue.offer("ERROR:" + response.substring(6));
            return;
        }

        if (response.equals("Goodbye!")) {
            responseQueue.offer("DISCONNECT");
            return;
        }


        if (response.startsWith("FILE_DATA:")) {

            StringBuilder fileData = new StringBuilder();
            try {

                String dataLine;
                while ((dataLine = in.readLine()) != null) {
                    if (dataLine.equals("END_FILE")) {
                        break;
                    }
                    fileData.append(dataLine);
                }
                responseQueue.offer("FILE_DATA:" + fileData.toString());
            } catch (IOException e) {
                responseQueue.offer("ERROR: Failed to read file data");
            }
            return;
        }

        if (response.startsWith("READY:")) {
            responseQueue.offer("READY:" + response.substring(6));
            return;
        }


        responseQueue.offer(response);
    }

    public void authenticate(String username, String password) throws IOException, InterruptedException {
        if (!isConnected()) throw new IOException("Not connected");

        // Clear the response queue of any previous messages (like greetings)
        responseQueue.clear();

        sendCommand(username + "," + password);
        this.username = username;


        String response = responseQueue.poll(5000, TimeUnit.MILLISECONDS);
        if (response == null) {
            throw new IOException("Authentication timeout");
        }

        if (response.startsWith("AUTH_SUCCESS:")) {
            System.out.println("Authenticated as client #" + clientId);
        } else if (response.startsWith("ERROR:")) {
            throw new IOException(response.substring(6));
        } else {
            throw new IOException("Authentication failed: " + response);
        }
    }

    public void sendCommand(String command) throws IOException {
        if (!isConnected()) throw new IOException("Not connected");


        sendQueue.offer(command);
    }

    public String sendAndWait(String command, long timeoutMs) throws IOException, InterruptedException {
        sendCommand(command);
        return responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public String sendAndWaitForResponse(String command, long timeoutMs) throws IOException, InterruptedException {
        if (!command.isEmpty()) {
            sendCommand(command);
        }

        StringBuilder fullResponse = new StringBuilder();
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < endTime) {
            String response = responseQueue.poll(100, TimeUnit.MILLISECONDS);
            if (response != null) {
                if (response.equals("CMD_END")) {
                    return fullResponse.length() > 0 ? fullResponse.toString().trim() : "";
                }
                if (response.equals("DISCONNECT")) {
                    return fullResponse.length() > 0 ? fullResponse.toString().trim() : null;
                }
                if (fullResponse.length() > 0) {
                    fullResponse.append("\n");
                }
                fullResponse.append(response);
            }
        }
        return fullResponse.length() > 0 ? fullResponse.toString().trim() : null;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public int getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public void stop() {
        running = false;
        disconnect();
        executor.shutdownNow();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
