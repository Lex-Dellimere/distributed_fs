package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;

public class GUIClient extends Application {
    private Connection connection;
    private CommandProcessor processor;

    private TextArea outputArea;
    private TextField commandField;
    private ListView<String> historyBox;
    private TextField hostField;
    private TextField portField;
    private Label userLabel;
    private Label roleLabel;
    private ListView<String> clientsList;
    private Button connectBtn;

    private final ObservableList<String> history = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) {
        connection = new Connection("localhost", 5555);
        processor = new CommandProcessor(connection, this::log);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // LEFT: User info, Client list and History
        VBox leftPane = new VBox(10);
        leftPane.setPrefWidth(250);
        leftPane.setPadding(new Insets(0, 10, 0, 0));
        
        userLabel = new Label("Username: Not logged in");
        roleLabel = new Label("Role: Guest");
        
        Label clientsHeader = new Label("Connected clients -");
        clientsList = new ListView<>();
        clientsList.setPrefHeight(200);
        
        historyBox = new ListView<>(history);
        historyBox.setPrefHeight(200);
        historyBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) commandField.setText(newVal);
        });

        leftPane.getChildren().addAll(userLabel, roleLabel, clientsHeader, clientsList, new Label("History"), historyBox);
        root.setLeft(leftPane);

        // RIGHT: Server and Port
        VBox rightPane = new VBox(10);
        rightPane.setPrefWidth(150);
        rightPane.setPadding(new Insets(0, 0, 0, 10));
        
        hostField = new TextField("localhost");
        hostField.setPromptText("Server Host");
        portField = new TextField("5555");
        portField.setPromptText("Port");
        connectBtn = new Button("Connect");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setOnAction(e -> handleConnect());
        
        rightPane.getChildren().addAll(new Label("Server"), hostField, new Label("Port"), portField, connectBtn);
        root.setRight(rightPane);

        // CENTER: Output Area
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        root.setCenter(outputArea);

        // BOTTOM: Input
        VBox bottomPane = new VBox(5);
        bottomPane.setPadding(new Insets(10, 0, 0, 0));
        
        HBox inputRow = new HBox(5);
        commandField = new TextField();
        commandField.setPromptText("Enter command here...");
        commandField.setPrefHeight(60);
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> handleCommand());
        
        Button sendBtn = new Button("Send");
        sendBtn.setPrefHeight(60);
        sendBtn.setPrefWidth(120);
        sendBtn.setOnAction(e -> handleCommand());
        inputRow.getChildren().addAll(commandField, sendBtn);
        
        bottomPane.getChildren().addAll(inputRow);
        root.setBottom(bottomPane);

        Scene scene = new Scene(root, 900, 600);
        String css = getClass().getResource("/style.css").toExternalForm();
        scene.getStylesheets().add(css);
        
        primaryStage.setTitle("Distributed File System Client");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (connection != null) connection.stop();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();

        log("Client UI Started. Please connect to a server.");
    }

    private void handleConnect() {
        if (connection.isConnected()) {
            connection.stop();
            connectBtn.setText("Connect");
            log("Disconnected from server.");
            updateUserInfo();
            return;
        }

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            log("Invalid port number.");
            return;
        }

        connection.setConnectionInfo(host, port);
        log("Connecting to " + host + ":" + port + "...");
        
        new Thread(() -> {
            connection.start();
            int attempts = 0;
            while (!connection.isConnected() && attempts < 10) {
                try {
                    Thread.sleep(500);
                    attempts++;
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            Platform.runLater(() -> {
                if (connection.isConnected()) {
                    log("Connected successfully!");
                    connectBtn.setText("Disconnect");
                    startStatusUpdateLoop();
                } else {
                    log("Failed to connect.");
                }
            });
        }).start();
    }

    private void handleCommand() {
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty()) return;
        
        if (!connection.isConnected()) {
            log("Not connected to server.");
            return;
        }

        if (!history.contains(cmd)) {
            history.add(0, cmd);
        }
        commandField.clear();
        log("> " + cmd);
        
        new Thread(() -> {
            processor.process(cmd);
            Platform.runLater(this::updateUserInfo);
        }).start();
    }

    private void updateUserInfo() {
        if (connection.isConnected()) {
            userLabel.setText("Username: " + (connection.getUsername().isEmpty() ? "Anonymous" : connection.getUsername()));
            roleLabel.setText("Role: " + connection.getRole());
        } else {
            userLabel.setText("Username: Not logged in");
            roleLabel.setText("Role: Guest");
        }
    }

    private void startStatusUpdateLoop() {
        Thread loop = new Thread(() -> {
            while (connection.isConnected()) {
                try {
                    // Periodically refresh client list
                    String response = connection.sendAndWaitForResponse("listclients", 2000);
                    if (response != null && !response.startsWith("ERROR:")) {
                        String[] lines = response.split("\n");
                        Platform.runLater(() -> {
                            clientsList.getItems().clear();
                            for (String line : lines) {
                                if (line.contains("(Offline)")) {
                                    clientsList.getItems().add("  " + line);
                                } else {
                                    clientsList.getItems().add(line);
                                }
                            }
                        });
                    }
                    
                    Platform.runLater(this::updateUserInfo);
                    Thread.sleep(5000);
                } catch (Exception e) {
                    break;
                }
            }
        });
        loop.setDaemon(true);
        loop.start();
    }

    private void log(String message) {
        Platform.runLater(() -> {
            outputArea.appendText(message + "\n");
            outputArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
