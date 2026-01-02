package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class ServerThread extends Thread {
    private final Socket socket;
    private final ArrayList<ServerThread> threadList;
    private PrintWriter output;

    public ServerThread(Socket socket, ArrayList<ServerThread> threads) {
        this.socket = socket;
        this.threadList = threads;
        // returning the output to the client - true statement is to flush the buffer otherwise we have to do it manually
        try {
            this.output = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // read input from client
            BufferedReader input = new BufferedReader( new InputStreamReader(socket.getInputStream()) );


            // inf loop for server
            String outputString;
            while((outputString = input.readLine()) != null) {
                if (outputString.equals("exit")) {
                    break;
                }
                printToAllClients(outputString);
                System.out.println("Server received " + outputString);
            }
        } catch (Exception e) {
            System.out.println("Error: " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void printToAllClients(String outString) {
        for (ServerThread sT : threadList) {
            sT.output.println(outString);
        }
    }
}
