package org.example;

import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Connection {
    private String host;
    private int port;
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private ExecutorService executor;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = false;
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
        if (running) {
            System.out.println("Connection already running");
            return;
        }
        running = true;
        sendQueue.clear();
        responseQueue.clear();
        executor = Executors.newVirtualThreadPerTaskExecutor();
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
        try {
            connect();
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            running = false;
            return;
        }

        while (running && isConnected()) {
            try {
                String command = sendQueue.poll(100, TimeUnit.MILLISECONDS);
                if (command != null && out != null) {
                    out.println(command);
                    System.out.println("[DEBUG] Sent: " + command);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        disconnect();
    }

    private void receiveLoop() {
        while (running && isConnected()) {
            try {
                if (in != null && in.ready()) {
                    String response = in.readLine();
                    if (response != null) {
                        System.out.println("[DEBUG] Received: " + response);
                        handleResponse(response);
                    } else {
                        System.out.println("[DEBUG] Server closed connection");
                        break;
                    }
                }
                Thread.sleep(10);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Receive error: " + e.getMessage());
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (running) {
            disconnect();
        }
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(5000);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println("Connected to server at " + host + ":" + port);
    }

    private void disconnect() {
        authenticated = false;
        clientId = -1;
        username = "";
        role = "Guest";
        
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore close errors
        }

        socket = null;
        out = null;
        in = null;
        System.out.println("Disconnected from server");
    }

    private void handleResponse(String response) {
        if (response == null) return;

        // Handle login success: "SUCCESS: Welcome back, <username> [<role>]"
        if (response.startsWith("SUCCESS: Welcome back,")) {
            Pattern pattern = Pattern.compile("SUCCESS: Welcome back, (.+?) \\[(.+?)]");
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                this.username = matcher.group(1);
                this.role = matcher.group(2);
                this.authenticated = true;
                responseQueue.offer("AUTH_SUCCESS");
                return;
            }
        }

        // Handle signup success
        if (response.equals("SUCCESS: User registered. Please login.")) {
            responseQueue.offer("SIGNUP_SUCCESS");
            return;
        }

        // Handle guest login success
        if (response.equals("SUCCESS: Logged in as GUEST")) {
            this.username = "guest";
            this.role = "guest";
            this.authenticated = true;
            responseQueue.offer("AUTH_SUCCESS");
            return;
        }

        // Skip server greeting/prompt messages
        if (response.equals("=== Distributed File System ===") ||
            response.startsWith("Available:") ||
            response.equals("Type 'help' for available commands")) {
            return;
        }

        // Handle SUCCESS responses
        if (response.startsWith("SUCCESS:")) {
            responseQueue.offer(response);
            return;
        }

        // Handle ERROR responses
        if (response.startsWith("ERROR:")) {
            responseQueue.offer(response);
            return;
        }

        // Handle disconnect
        if (response.equals("Goodbye!")) {
            responseQueue.offer("DISCONNECT");
            return;
        }

        // Handle file data download
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
                responseQueue.offer("FILE_DATA:" + fileData);
            } catch (IOException e) {
                responseQueue.offer("ERROR: Failed to read file data");
            }
            return;
        }

        // Handle upload ready signal
        if (response.startsWith("READY:")) {
            responseQueue.offer(response);
            return;
        }
        if (response.equals("CMD_END")) {
            responseQueue.offer(response);
            return;
        }

        // All other responses
        responseQueue.offer(response);
    }

    public String authenticate(String authCommand) throws IOException, InterruptedException {
        if (!isConnected()) throw new IOException("Not connected");

        // Clear the response queue of any previous messages (like greetings)
        responseQueue.clear();

        // Wait a moment for server greeting messages to be processed
        Thread.sleep(200);
        responseQueue.clear();

        sendCommand(authCommand);

        // Wait for authentication response
        long timeout = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < timeout) {
            String response = responseQueue.poll(100, TimeUnit.MILLISECONDS);
            if (response != null) {
                if (response.equals("AUTH_SUCCESS")) {
                    return "SUCCESS";
                } else if (response.equals("SIGNUP_SUCCESS")) {
                    return "SIGNUP_SUCCESS";
                } else if (response.startsWith("ERROR:")) {
                    return response;
                }
            }
        }

        throw new IOException("Authentication timeout");
    }

    /**
     * Hashes a password using BCrypt.
     *
     * @param password the plaintext password
     * @return the BCrypt hashed password
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
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
                    return !fullResponse.isEmpty() ? fullResponse.toString().trim() : "";
                }
                if (response.equals("DISCONNECT")) {
                    return !fullResponse.isEmpty() ? fullResponse.toString().trim() : null;
                }
                if (!fullResponse.isEmpty()) {
                    fullResponse.append("\n");
                }
                fullResponse.append(response);
            }
        }
        return !fullResponse.isEmpty() ? fullResponse.toString().trim() : null;
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
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sendQueue.clear();
        responseQueue.clear();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
