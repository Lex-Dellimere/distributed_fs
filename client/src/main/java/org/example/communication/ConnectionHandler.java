package org.example.communication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConnectionHandler implements Runnable {
    private final String serverHostname;
    private final int serverPort;
    private Socket clientSocket;
    private PrintWriter output;
    private BufferedReader input;
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private final Object connectionLock = new Object();
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private volatile int clientId = -1;

    public ConnectionHandler(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        running = true;

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                synchronized (connectionLock) {
                    if (!connected) {
                        connectToServer();
                    }
                }

                if (connected && !authenticated) {
                    Thread.sleep(100);
                    continue;
                }

                if (authenticated) {
                    String command = commandQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (command != null && !command.isEmpty()) {
                        output.println(command);
                        System.out.println("[DEBUG] Sent command: " + command);
                    }

                    if (input.ready()) {
                        String response = input.readLine();
                        if (response != null) {
                            if (response.startsWith("RESPONSE:")) {
                                String content = response.substring(9).trim();
                                responseQueue.put(content);
                            } else if (response.startsWith("ASYNC:")) {
                                String message = response.substring(6).trim();
                                System.out.println("SERVER MESSAGE: " + message);
                            } else if (response.startsWith("ERROR:")) {
                                String error = response.substring(6).trim();
                                System.err.println("SERVER ERROR: " + error);
                            } else {
                                System.out.println("SERVER: " + response);
                            }
                        }
                    }
                }

                Thread.sleep(10);

            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
                disconnect();
                if (running) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
                disconnect();
            }
        }
        disconnect();
    }

    private void connectToServer() throws IOException {
        System.out.println("Connecting to " + serverHostname + ":" + serverPort + "...");
        clientSocket = new Socket(serverHostname, serverPort);
        output = new PrintWriter(clientSocket.getOutputStream(), true);
        input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        connected = true;
        System.out.println("Connected to server!");
    }

    private void disconnect() {
        synchronized (connectionLock) {
            connected = false;
            authenticated = false;
            clientId = -1;
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
            commandQueue.clear();
            responseQueue.clear();
            System.out.println("Disconnected from server");
        }
    }

    public boolean authenticate(String username, String password) throws IOException {
        synchronized (connectionLock) {
            if (!connected) {
                throw new IOException("Not connected to server");
            }

            output.println("AUTH:" + username + "," + password);
            String response = input.readLine();

            if (response != null) {
                try {
                    if (response.startsWith("CLIENT_ID:")) {
                        String idStr = response.substring(10);
                        clientId = Integer.parseInt(idStr);
                        authenticated = clientId >= 0;
                        if (authenticated) {
                            System.out.println("Authenticated! Client ID: " + clientId);
                        } else {
                            System.out.println("Authentication failed (negative ID)");
                        }
                    } else {
                        System.out.println("Authentication failed: " + response);
                        authenticated = false;
                    }
                    return authenticated;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid authentication response format: " + response);
                    return false;
                }
            } else {
                System.out.println("No response from server.");
                return false;
            }
        }
    }

    public void sendCommand(String command) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to server");
        }
        if (!authenticated) {
            throw new IOException("Not authenticated");
        }
        commandQueue.offer(command);
    }

    public String getResponse(long timeoutMillis) throws InterruptedException {
        return responseQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public String sendCommandAndWait(String command, long timeoutMillis) throws IOException, InterruptedException {
        sendCommand(command);
        return getResponse(timeoutMillis);
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public int getClientId() {
        return clientId;
    }

    public void stop() {
        running = false;
        disconnect();
    }
}