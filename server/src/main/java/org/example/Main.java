package org.example;

import org.example.server.ConnectionHandler;

public class Main {
    void main(String[] args) {
        new Main().run();
    }

    void run() {
        var connectionHandler = new ConnectionHandler(5555);
        connectionHandler.runServer();
    }
}