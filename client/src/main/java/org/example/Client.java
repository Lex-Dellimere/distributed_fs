package org.example;

import java.util.Scanner;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    public static void main(String[] args) {
        new Client().run();
    }

    private void run() {
        Scanner scanner = new Scanner(System.in);
        Connection connection = new Connection(HOST, PORT);

        System.out.println("=========================================");
        System.out.println("   Distributed File System Client");
        System.out.println("=========================================");
        System.out.println("Connecting to " + HOST + ":" + PORT + "...");


        connection.start();


        if (!waitForConnection(connection)) {
            System.out.println("Failed to connect. Exiting.");
            System.exit(1);
        }


        while (connection.isConnected() && !connection.isAuthenticated()) {
            try {
                System.out.println("\n--- Authentication Required ---");
                System.out.print("Username: ");
                String username = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();
                System.out.println("Authenticating...");

                connection.authenticate(username, password);

                if (!connection.isAuthenticated()) {
                    System.out.println("Authentication failed. Please try again.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        if (connection.isAuthenticated()) {
            new REPL(connection, scanner).run();
        } else {
            System.out.println("Failed to authenticate. Exiting.");
        }

        scanner.close();
        connection.stop();
        System.out.println("Client stopped");
    }

    private boolean waitForConnection(Connection connection) {
        System.out.print("Connecting");
        int attempts = 0;
        while (!connection.isConnected() && attempts < 30) {
            try {
                Thread.sleep(500);
                System.out.print(".");
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.out.println();

        return connection.isConnected();
    }
}
