package org.example;

import org.example.communication.ConnectionHandler;
import org.example.frontend.REPL;

import java.util.Scanner;

public class Main {
    private static final String SERVER_HOSTNAME = "localhost";
    private static final int SERVER_PORT = 5555;

    void main(String[] args) {
        new Main().run();
    }

    void run() {
        var scanner = new Scanner(System.in);

        System.out.println("=== Client Application ===");
        System.out.println("Connecting to server at " + SERVER_HOSTNAME + ":" + SERVER_PORT);

        var connectionHandler = new ConnectionHandler(SERVER_HOSTNAME, SERVER_PORT);

        Thread connectionThread = Thread.ofVirtual()
            .name("connection-handler")
            .uncaughtExceptionHandler((t, e) ->
                System.err.println("Connection thread error: " + e.getMessage()))
            .start(connectionHandler);

        waitForConnection(connectionHandler);

        authenticate(connectionHandler, scanner);

        var repl = new REPL(connectionHandler);
        Thread replThread = Thread.ofVirtual()
            .name("repl-handler")
            .uncaughtExceptionHandler((t, e) ->
                System.err.println("REPL thread error: " + e.getMessage()))
            .start(repl);

        Thread monitorThread = Thread.ofVirtual()
            .name("status-monitor")
            .start(() -> monitorStatus(connectionHandler));

        try {
            replThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted");
        }

        shutdown(connectionHandler, connectionThread, monitorThread, scanner);
    }

    private void waitForConnection(ConnectionHandler handler) {
        System.out.print("Waiting for connection");

        int attempts = 0;
        final int maxAttempts = 20;

        while (!handler.isConnected() && attempts < maxAttempts) {
            try {
                Thread.sleep(500);
                System.out.print(".");
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println();

        if (!handler.isConnected()) {
            System.out.println("Failed to connect to server. Please check server status.");
            System.exit(1);
        }
    }

    private void authenticate(ConnectionHandler handler, Scanner scanner) {
        while (!handler.isAuthenticated()) {
            try {
                System.out.println("\n=== Authentication ===");
                System.out.print("Username: ");
                String username = scanner.nextLine().trim();

                System.out.print("Password: ");
                String password = scanner.nextLine().trim();

                if (handler.authenticate(username, password)) {
                    System.out.println("Authentication successful!");
                    break;
                } else {
                    System.out.println("Authentication failed. Please try again.");

                    if (!handler.isConnected()) {
                        System.out.println("Connection lost. Attempting to reconnect...");
                        waitForConnection(handler);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());

                if (!handler.isConnected()) {
                    System.out.println("Attempting to reconnect...");
                    waitForConnection(handler);
                }
            }
        }
    }

    private void monitorStatus(ConnectionHandler handler) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(15000);

                if (!handler.isConnected()) {
                    System.out.println("\n[STATUS] Disconnected from server");
                } else if (!handler.isAuthenticated()) {
                    System.out.println("\n[STATUS] Connected but not authenticated");
                } else {
                    System.out.println("\n[STATUS] Connected as Client #" + handler.getClientId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void shutdown(ConnectionHandler handler, Thread connectionThread,
                          Thread monitorThread, Scanner scanner) {
        System.out.println("\nShutting down...");

        handler.stop();
        monitorThread.interrupt();
        connectionThread.interrupt();
        scanner.close();

        try {
            connectionThread.join(2000);
            monitorThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Application terminated.");
    }
}