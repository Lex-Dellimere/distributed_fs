package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class GUIClient extends Application {
    private Connection connection;
    private CommandProcessor processor;
    private CredentialStore credentialStore;

    private TextArea outputArea;
    private TextArea debugArea;
    private TextField commandField;
    //private TextField commandField;
    private ListView<String> historyBox;
    private TextField hostField;
    private TextField portField;
    private Label userLabel;
    private Label roleLabel;
    private ListView<String> clientsList;
    private Button connectBtn;
    private StackPane notificationPane;
    private BorderPane root;

    private final ObservableList<String> history = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) {
        connection = new Connection("localhost", 5555);
        processor = new CommandProcessor(connection, this::log, this::showNotification);
        credentialStore = new CredentialStore();

        // Redirect System.err and System.out to debug area
        setupDebugLogging();

        root = new BorderPane();
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
        
        Button logoutBtn = new Button("Clear Saved Login");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> {
            credentialStore.clearCredentials();
            showNotification("Saved credentials cleared", "info");
        });
        
        rightPane.getChildren().addAll(new Label("Server"), hostField, new Label("Port"), portField, connectBtn, logoutBtn);
        root.setRight(rightPane);

        // CENTER: Tabbed output area
        TabPane centerTabs = new TabPane();
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Main output tab
        Tab outputTab = new Tab("Output");
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputTab.setContent(outputArea);

        // Debug tab
        Tab debugTab = new Tab("Debug");
        debugArea = new TextArea();
        debugArea.setEditable(false);
        debugArea.setWrapText(true);
        debugArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        VBox debugBox = new VBox(5);
        Button clearDebugBtn = new Button("Clear Debug Log");
        clearDebugBtn.setOnAction(e -> debugArea.clear());
        debugBox.getChildren().addAll(clearDebugBtn, debugArea);
        VBox.setVgrow(debugArea, Priority.ALWAYS);
        debugBox.setPadding(new Insets(5));
        debugTab.setContent(debugBox);

        centerTabs.getTabs().addAll(outputTab, debugTab);
        root.setCenter(centerTabs);

        // BOTTOM: Input
        VBox bottomPane = new VBox(5);
        bottomPane.setPadding(new Insets(10, 0, 0, 0));
        
        HBox inputRow = new HBox(5);
        commandField = new TextField();
        commandField.setPromptText("Enter command here...");
        commandField.setPrefHeight(60);
        commandField.setDisable(true); // Disabled until authenticated
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> handleCommand());

        Button sendBtn = new Button("Send");
        sendBtn.setPrefHeight(60);
        sendBtn.setPrefWidth(120);
        sendBtn.setOnAction(e -> handleCommand());
        inputRow.getChildren().addAll(commandField, sendBtn);
        
        bottomPane.getChildren().addAll(inputRow);
        root.setBottom(bottomPane);

        // Wrap root in StackPane for notifications
        StackPane mainStack = new StackPane();
        mainStack.getChildren().add(root);

        // Notification pane (initially empty, on top)
        notificationPane = new StackPane();
        notificationPane.setPickOnBounds(false); // Allow clicks through empty space
        notificationPane.setAlignment(Pos.TOP_CENTER);
        notificationPane.setPadding(new Insets(10));
        mainStack.getChildren().add(notificationPane);

        Scene scene = new Scene(mainStack, 900, 600);
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
        debugLog("[INIT] Client initialized");
        debugLog("[INIT] Credential store: " + (credentialStore.hasStoredCredentials() ? "Found" : "Empty"));
    }

    private void setupDebugLogging() {
        // Capture System.out
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            private final StringBuilder buffer = new StringBuilder();
            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString();
                    originalOut.println(line);
                    debugLog("[OUT] " + line);
                    buffer.setLength(0);
                } else {
                    buffer.append((char) b);
                }
            }
        }));

        // Capture System.err
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            private final StringBuilder buffer = new StringBuilder();
            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString();
                    originalErr.println(line);
                    debugLog("[ERR] " + line);
                    buffer.setLength(0);
                } else {
                    buffer.append((char) b);
                }
            }
        }));
    }

    private void debugLog(String message) {
        Platform.runLater(() -> {
            if (debugArea != null) {
                String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
                debugArea.appendText("[" + timestamp + "] " + message + "\n");
                debugArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void handleConnect() {
        debugLog("[CONNECT] Button clicked, current state: " + (connection.isConnected() ? "connected" : "disconnected"));
        if (connection.isConnected()) {
            debugLog("[CONNECT] Clearing credentials and disconnecting");
            credentialStore.clearCredentials();
            connection.stop();
            connectBtn.setText("Connect");
            connectBtn.setDisable(false);
            showNotification("Disconnected from server", "info");
            updateUserInfo();
            commandField.setDisable(true);
            return;
        }

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            debugLog("[CONNECT] Invalid port: " + portField.getText());
            showNotification("Invalid port number", "error");
            return;
        }

        debugLog("[CONNECT] Attempting connection to " + host + ":" + port);
        connectBtn.setDisable(true);
        connectBtn.setText("Connecting...");
        connection.setConnectionInfo(host, port);
        showNotification("Connecting to " + host + ":" + port + "...", "info");

        new Thread(() -> {
            try {
                connection.start();
                int attempts = 0;
                while (!connection.isConnected() && attempts < 20) {
                    Thread.sleep(250);
                    attempts++;
                }

                int finalAttempts = attempts;
                Platform.runLater(() -> {
                    if (connection.isConnected()) {
                        debugLog("[CONNECT] Connection established");
                        showNotification("Connected to server", "success");
                        handleAuthentication();
                    } else {
                        debugLog("[CONNECT] Connection failed after " + finalAttempts + " attempts");
                        showNotification("Failed to connect to server", "error");
                        connectBtn.setText("Connect");
                        connectBtn.setDisable(false);
                        connection.stop();
                    }
                });
            } catch (Exception e) {
                debugLog("[CONNECT] Exception: " + e.getMessage());
                Platform.runLater(() -> {
                    showNotification("Connection error: " + e.getMessage(), "error");
                    connectBtn.setText("Connect");
                    connectBtn.setDisable(false);
                    connection.stop();
                });
            }
        }).start();
    }

    private void handleAuthentication() {
        debugLog("[AUTH] Starting authentication");
        // Check for stored credentials first
        if (credentialStore.hasStoredCredentials()) {
            debugLog("[AUTH] Found stored credentials, attempting auto-login");
            Map<String, String> creds = credentialStore.loadCredentials();
            if (creds != null && creds.containsKey("username") && creds.containsKey("password")) {
                connectBtn.setText("Authenticating...");
                attemptLogin(creds.get("username"), creds.get("password"), true);
                return;
            }
        }

        debugLog("[AUTH] No stored credentials, showing auth dialog");
        // Show auth dialog if no stored credentials
        showAuthDialog();
    }

    private void showAuthDialog() {
        AuthDialog dialog = new AuthDialog();
        Optional<AuthResult> result = dialog.showAndWait();

        if (result.isEmpty()) {
            debugLog("[AUTH] User cancelled authentication");
            showNotification("Authentication cancelled", "warning");
            connectBtn.setText("Connect");
            connectBtn.setDisable(false);
            connection.stop();
            return;
        }

        AuthResult authResult = result.get();
        debugLog("[AUTH] User selected: " + authResult.type());
        connectBtn.setText("Authenticating...");

        new Thread(() -> {
            try {
                String authCommand;
                String hashedPassword = null;

                switch (authResult.type()) {
                    case LOGIN:
                        hashedPassword = Connection.hashPassword(authResult.password());
                        authCommand = "login " + authResult.username() + " " + hashedPassword;
                        break;
                    case SIGNUP:
                        hashedPassword = Connection.hashPassword(authResult.password());
                        authCommand = "signup " + authResult.username() + " " + hashedPassword;
                        break;
                    case GUEST:
                        authCommand = "guest";
                        break;
                    default:
                        Platform.runLater(() -> {
                            showNotification("Invalid authentication type", "error");
                            connectBtn.setText("Connect");
                            connectBtn.setDisable(false);
                        });
                        connection.stop();
                        return;
                }

                String response = connection.authenticate(authCommand);
                final String finalHashedPassword = hashedPassword;

                Platform.runLater(() -> {
                    if (response.equals("SUCCESS")) {
                        debugLog("[AUTH] Login successful: " + connection.getUsername() + " [" + connection.getRole() + "]");
                        if (authResult.type() == AuthType.LOGIN && finalHashedPassword != null) {
                            credentialStore.saveCredentials(authResult.username(), finalHashedPassword);
                            debugLog("[AUTH] Credentials saved to store");
                        }
                        showNotification("Logged in as " + connection.getUsername() + " [" + connection.getRole() + "]", "success");
                        connectBtn.setText("Disconnect");
                        connectBtn.setDisable(false);
                        commandField.setDisable(false);
                        updateUserInfo();
                        startStatusUpdateLoop();
                    } else if (response.equals("SIGNUP_SUCCESS")) {
                        debugLog("[AUTH] Signup successful, reconnecting for login");
                        showNotification("Signup successful! Reconnecting to login...", "success");
                        connectBtn.setText("Connect");
                        connectBtn.setDisable(false);
                        connection.stop();
                        new Thread(() -> {
                            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                            Platform.runLater(this::handleConnect);
                        }).start();
                    } else if (response.startsWith("ERROR:")) {
                        debugLog("[AUTH] Authentication failed: " + response);
                        showNotification("Authentication failed: " + response.substring(6).trim(), "error");
                        connectBtn.setText("Connect");
                        connectBtn.setDisable(false);
                        connection.stop();
                    } else {
                        debugLog("[AUTH] Unknown response: " + response);
                        showNotification("Authentication failed: Unknown response", "error");
                        connectBtn.setText("Connect");
                        connectBtn.setDisable(false);
                        connection.stop();
                    }
                });

            } catch (Exception e) {
                debugLog("[AUTH] Exception during authentication: " + e.getMessage());
                Platform.runLater(() -> {
                    showNotification("Authentication error: " + e.getMessage(), "error");
                    connectBtn.setText("Connect");
                    connectBtn.setDisable(false);
                    connection.stop();
                });
            }
        }).start();
    }

    private void attemptLogin(String username, String hashedPassword, boolean isStoredCredential) {
        debugLog("[LOGIN] Attempting login for: " + username + " (stored: " + isStoredCredential + ")");
        new Thread(() -> {
            try {
                String authCommand = "login " + username + " " + hashedPassword;
                String response = connection.authenticate(authCommand);

                Platform.runLater(() -> {
                    if (response.equals("SUCCESS")) {
                        debugLog("[LOGIN] Auto-login successful: " + connection.getUsername());
                        showNotification("Logged in as " + connection.getUsername() + " [" + connection.getRole() + "]", "success");
                        connectBtn.setText("Disconnect");
                        connectBtn.setDisable(false);
                        commandField.setDisable(false);
                        updateUserInfo();
                        startStatusUpdateLoop();
                    } else {
                        debugLog("[LOGIN] Login failed: " + response);
                        if (isStoredCredential) {
                            credentialStore.clearCredentials();
                            debugLog("[LOGIN] Cleared invalid stored credentials");
                            showNotification("Stored credentials invalid. Please login again.", "warning");
                            showAuthDialog();
                        } else {
                            showNotification("Authentication failed", "error");
                            connectBtn.setText("Connect");
                            connectBtn.setDisable(false);
                            connection.stop();
                        }
                    }
                });
            } catch (Exception e) {
                debugLog("[LOGIN] Exception: " + e.getMessage());
                Platform.runLater(() -> {
                    if (isStoredCredential) {
                        credentialStore.clearCredentials();
                        showNotification("Login failed. Please try again.", "error");
                        showAuthDialog();
                    } else {
                        showNotification("Authentication error: " + e.getMessage(), "error");
                        connectBtn.setText("Connect");
                        connectBtn.setDisable(false);
                        connection.stop();
                    }
                });
            }
        }).start();
    }

    private void handleCommand() {
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty()) return;

        if (!connection.isConnected()) {
            debugLog("[CMD] Command rejected: not connected");
            showNotification("Not connected to server", "error");
            return;
        }

        if (!connection.isAuthenticated()) {
            debugLog("[CMD] Command rejected: not authenticated");
            showNotification("Not authenticated. Please login first", "error");
            return;
        }

        debugLog("[CMD] Executing: " + cmd);
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
            while (connection.isConnected() && connection.isAuthenticated()) {
                try {
                    // Periodically refresh client list (only if authenticated)
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

    /**
     * Shows a notification popup at the top of the screen
     * @param message the message to display
     * @param type notification type: "error", "success", "info", "warning"
     */
    private void showNotification(String message, String type) {
        Platform.runLater(() -> {
            // Create notification label
            Label notification = new Label(message);
            notification.setWrapText(true);
            notification.setMaxWidth(600);
            notification.setPadding(new Insets(15, 20, 15, 20));
            notification.setStyle(getNotificationStyle(type));

            // Add to notification pane
            notificationPane.getChildren().add(notification);

            // Auto-hide after 4 seconds
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
                notificationPane.getChildren().remove(notification);
            }));
            timeline.play();

            // Allow manual dismiss on click
            notification.setOnMouseClicked(e -> {
                notificationPane.getChildren().remove(notification);
                timeline.stop();
            });
        });
    }

    private String getNotificationStyle(String type) {
        String baseStyle = "-fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2); " +
                "-fx-font-size: 14px; -fx-cursor: hand;";

        return switch (type.toLowerCase()) {
            case "error" -> baseStyle + " -fx-background-color: #d32f2f; -fx-text-fill: white;";
            case "success" -> baseStyle + " -fx-background-color: #388e3c; -fx-text-fill: white;";
            case "warning" -> baseStyle + " -fx-background-color: #f57c00; -fx-text-fill: white;";
            default -> baseStyle + " -fx-background-color: #1976d2; -fx-text-fill: white;"; // info
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
