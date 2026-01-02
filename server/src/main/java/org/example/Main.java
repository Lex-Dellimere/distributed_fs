package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;


public class Main {
    static void main(String[] args) {

        System.out.println("Starting the server");

        ArrayList<ServerThread> threadList = new ArrayList<>();

        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ServerThread serverThread = new ServerThread(socket, threadList);
                // start the thread
                threadList.add(serverThread);
                serverThread.start();

                // get the list of currently running threads
            }
        } catch (Exception e) {
            System.out.println("Error in main: " + Arrays.toString(e.getStackTrace()));
        }

        System.out.println("Ending the server");
    }
}
