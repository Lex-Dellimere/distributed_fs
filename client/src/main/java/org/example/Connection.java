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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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

    // Callback invoked when the connection is closed unexpectedly (or with a reason)
    private volatile Consumer<String> onConnectionClosed;
    private final AtomicBoolean closedNotified = new AtomicBoolean(false);

    // Heartbeat configuration
    private static final long PING_INTERVAL_MS = 5000; // send ping every 5s
    private static final long PONG_TIMEOUT_MS = 12000; // consider dead if no pong in 12s
    private volatile long lastPongTimestamp = 0;
    private Future<?> heartbeatTask;
    private Future<?> pongMonitorTask;

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

    public void setOnConnectionClosed(Consumer<String> cb) {
        this.onConnectionClosed = cb;
    }

    private void notifyConnectionClosed(String reason) {
        if (closedNotified.compareAndSet(false, true)) {
            if (onConnectionClosed != null) {
                try {
                    onConnectionClosed.accept(reason != null ? reason : "Connection closed");
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void start() {
        if (running) {
            System.out.println("[DEBUG] Connection already running");
            return;
        }
        running = true;
        closedNotified.set(false);
        sendQueue.clear();
        responseQueue.clear();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(this::connectionLoop);
        executor.submit(this::receiveLoop);
        System.out.println("[DEBUG] Connection threads started");
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    private void connectionLoop() {
        System.out.println("[DEBUG] Connection loop started");
        try {
            connect();
            System.out.println("[DEBUG] Socket connected successfully");
        } catch (IOException e) {
            System.err.println("[DEBUG] Failed to connect: " + e.getMessage());
            e.printStackTrace();
            running = false;
            notifyConnectionClosed("Failed to connect: " + e.getMessage());
            return;
        }

        // Start heartbeat and monitor tasks after successful connect
        lastPongTimestamp = System.currentTimeMillis();
        try {
            heartbeatTask = executor.submit(this::heartbeatLoop);
            pongMonitorTask = executor.submit(this::pongMonitorLoop);
        } catch (Exception ignored) {}

        System.out.println("[DEBUG] Connection loop: entering send loop");
        while (running && isConnected()) {
            try {
                String command = sendQueue.poll(100, TimeUnit.MILLISECONDS);
                if (command != null && out != null) {
                    out.println(command);
                    out.flush();
                    System.out.println("[DEBUG] Sent: " + command);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[DEBUG] Send error: " + e.getMessage());
                break;
            }
        }
        System.out.println("[DEBUG] Connection loop ending, running=" + running + ", connected=" + isConnected());
        disconnect();
    }

    private void heartbeatLoop() {
        try {
            while (running && isConnected()) {
                try {
                    // send a lightweight ping; server should respond with PONG
                    if (isConnected()) {
                        sendQueue.offer("PING");
                    }
                    Thread.sleep(PING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[DEBUG] Heartbeat error: " + e.getMessage());
        }
    }

    private void pongMonitorLoop() {
        try {
            while (running && isConnected()) {
                long now = System.currentTimeMillis();
                if (now - lastPongTimestamp > PONG_TIMEOUT_MS) {
                    System.err.println("[DEBUG] Pong timeout: lastPong=" + lastPongTimestamp + ", now=" + now);
                    notifyConnectionClosed("Heartbeat timeout");
                    // break out and allow disconnect to run
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[DEBUG] Pong monitor error: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        System.out.println("[DEBUG] Receive loop started");

        int waitCount = 0;
        while (in == null && running && waitCount < 50) {
            try {
                Thread.sleep(50);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (in == null) {
            System.err.println("[DEBUG] Receive loop: input stream not ready");
            notifyConnectionClosed("Input stream not ready");
            return;
        }

        System.out.println("[DEBUG] Receive loop: input stream ready, starting to read");

        while (running && isConnected()) {
            try {
                if (in != null) {
                    if (in.ready()) {
                        String response = in.readLine();
                        if (response != null) {
                            System.out.println("[DEBUG] Received: " + response);
                            // Handle PONG (heartbeat) directly so we can update lastPongTimestamp
                            if (response.equalsIgnoreCase("PONG")) {
                                lastPongTimestamp = System.currentTimeMillis();
                                // don't queue or show PONG to UI
                                continue;
                            }
                            handleResponse(response);
                        } else {
                            System.out.println("[DEBUG] Server closed connection");
                            notifyConnectionClosed("Server closed connection");
                            break;
                        }
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[DEBUG] Receive error: " + e.getMessage());
                    notifyConnectionClosed("Receive error: " + e.getMessage());
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[DEBUG] Receive loop ending");
        if (running) {
            disconnect();
        }
    }

    private void connect() throws IOException {
        System.out.println("[DEBUG] Attempting to create socket to " + host + ":" + port);
        socket = new Socket(host, port);
        System.out.println("[DEBUG] Socket created, setting up streams");
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println("[DEBUG] Connected to server at " + host + ":" + port);
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

        }

        // Cancel heartbeat/monitor tasks if running
        try {
            if (heartbeatTask != null) heartbeatTask.cancel(true);
        } catch (Exception ignored) {}
        try {
            if (pongMonitorTask != null) pongMonitorTask.cancel(true);
        } catch (Exception ignored) {}

        socket = null;
        out = null;
        in = null;
        System.out.println("Disconnected from server");
        // Ensure UI is notified even if disconnect is called directly
        notifyConnectionClosed("Disconnected");
    }

    private void handleResponse(String response) {
        if (response == null) return;

        
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

        
        if (response.equals("SUCCESS: User registered. Please login.")) {
            responseQueue.offer("SIGNUP_SUCCESS");
            return;
        }

        
        if (response.equals("SUCCESS: Logged in as GUEST")) {
            this.username = "guest";
            this.role = "guest";
            this.authenticated = true;
            responseQueue.offer("AUTH_SUCCESS");
            return;
        }

        
        if (response.equals("=== Distributed File System ===") ||
            response.startsWith("Available:") ||
            response.equals("Type 'help' for available commands")) {
            return;
        }

        
        if (response.startsWith("SUCCESS:")) {
            responseQueue.offer(response);
            return;
        }

        
        if (response.startsWith("ERROR:")) {
            responseQueue.offer(response);
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
                responseQueue.offer("FILE_DATA:" + fileData);
            } catch (IOException e) {
                responseQueue.offer("ERROR: Failed to read file data");
            }
            return;
        }

        
        if (response.startsWith("READY:")) {
            responseQueue.offer(response);
            return;
        }
        if (response.equals("CMD_END")) {
            responseQueue.offer(response);
            return;
        }

        
        responseQueue.offer(response);
    }

    public String authenticate(String authCommand) throws IOException, InterruptedException {
        if (!isConnected()) throw new IOException("Not connected");

        
        responseQueue.clear();

        
        Thread.sleep(200);
        responseQueue.clear();

        sendCommand(authCommand);

        
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
        boolean connected = socket != null && socket.isConnected() && !socket.isClosed();
        if (!connected && socket != null) {
            System.out.println("[DEBUG] isConnected check: socket=" + socket + ", isConnected=" + socket.isConnected() + ", isClosed=" + socket.isClosed());
        }
        return connected;
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
        // Mark as already notified to suppress the onConnectionClosed callback for intentional stops
        closedNotified.set(true);
        // Cancel heartbeat/monitor to avoid racing notify
        try { if (heartbeatTask != null) heartbeatTask.cancel(true); } catch (Exception ignored) {}
        try { if (pongMonitorTask != null) pongMonitorTask.cancel(true); } catch (Exception ignored) {}
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
