package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 5000)) {
            // return the output to the server : true statement to flush the buffer otherwise manual
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

            // taking the user input
            Scanner scanner = new Scanner(System.in);
            String userInput;
            String clientName = "empty";

            ClientRunnable clientRun = new ClientRunnable(socket);

            new Thread(clientRun).start();
            // loop closes when user enters exit command

            do {
                if (clientName.equals("empty")) {
                    System.out.println("Enter your name ");
                    userInput = scanner.nextLine();
                    clientName = userInput;
                    output.println(userInput);
                } else {
                    String message = ( "(" + clientName + ")" + " message: " );
                    System.out.println(message);
                    userInput = scanner.nextLine();
                    output.println(message + " " + userInput);
                }
            } while (!userInput.equals("exit"));


        } catch (Exception e) {
            System.out.println("Exception in client main: " + Arrays.toString(e.getStackTrace()));
        }
    }
}
