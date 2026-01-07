package org.example.manager;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import org.example.config.ServerConfig;
import org.example.config.DatabaseConfig;
import org.example.db.DatabaseManager;
import org.example.db.User;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class FreeSRSManager extends Application {
    private ServerConfig config;
    private DatabaseManager dbManager;
    private ListView<String> userListView;
    private Label statusLabel;
    private Label serverStatusLabel;
    private Process serverProcess;
    private TextArea serverLog;
    private TextArea connectionLog;
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final Set<String> BUILTIN_ROLES = Set.of("admin", "guest");
    private StackPane rootStack;
    private VBox errorBubbleBox;
    private Timer healthTimer;
    private Timer connectionLogTimer;
    private long healthStartTime = System.currentTimeMillis();
    private static final String HEALTH_HISTORY_FILE = "health_history.json";
    private static final int MAX_HISTORY_POINTS = 43200; 

    
    private final Map<String, List<HealthDataPoint>> healthHistory = new HashMap<>();
    private String currentMetric = "CPU %";
    private String currentRange = "Day";
    private Label connLabel, memLabel, sysMemLabel, cpuLabel, threadLabel, dbLabel, uptimeLabel;
    private LineChart<Number, Number> healthChart;

    static class HealthDataPoint {
        long time;
        double value;
        HealthDataPoint(long t, double v) { time = t; value = v; }
    }

    @Override
    public void start(Stage primaryStage) {
        config = ServerConfig.load();

        
        try {
            dbManager = new DatabaseManager(config);
            dbManager.initialize();
        } catch (Exception e) {
            
            System.err.println("DB init warning: " + e.getMessage());
        }

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(createConfigTab());
        tabPane.getTabs().add(createControlTab());
        tabPane.getTabs().add(createUsersTab());
        tabPane.getTabs().add(createRolesTab());
        tabPane.getTabs().add(createHealthTab());
        tabPane.getTabs().add(createConnectionLogsTab());

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        statusLabel = new Label("Ready");
        root.setBottom(new HBox(10, new Label("Status:"), statusLabel));

        
        rootStack = new StackPane(root);
        errorBubbleBox = new VBox(8);
        errorBubbleBox.setMouseTransparent(true);
        errorBubbleBox.setPickOnBounds(false);
        errorBubbleBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        rootStack.getChildren().add(errorBubbleBox);

        Scene scene = new Scene(rootStack, 900, 600);
        
        try {
            scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        primaryStage.setTitle("FreeDRS Manager");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            saveHealthHistory();
            stopHealthMonitor();
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroyForcibly();
            }
            exec.shutdownNow();
            Platform.exit();
            System.exit(0);
        });

        loadHealthHistory();
        startHealthMonitor();
    }

    private void showErrorBubble(String msg) {
        showBubble(msg, "#ff4444");
    }

    private void showSuccessBubble(String msg) {
        showBubble(msg, "#44ff44");
    }

    private void showInfoBubble(String msg) {
        showBubble(msg, "#4488ff");
    }

    private void showBubble(String msg, String color) {
        Label bubble = new Label(msg);
        bubble.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 12 24; -fx-font-size: 15px; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, #222, 8, 0.5, 0, 2);");
        bubble.setOpacity(0);
        errorBubbleBox.getChildren().add(bubble);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(350), bubble);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
        fadeIn.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(ev -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), bubble);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(ev2 -> errorBubbleBox.getChildren().remove(bubble));
                fadeOut.play();
            });
            pause.play();
        });
    }

    private Tab createConfigTab() {
        Tab tab = new Tab("Configuration");
        tab.setClosable(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField portField = new TextField(String.valueOf(config.getPort()));
        TextField pathField = new TextField(config.getResourcesPath());
        TextField adminUserField = new TextField(config.getAdminUser() == null ? "" : config.getAdminUser());
        PasswordField adminPassField = new PasswordField();

        
        Label sqlitePathLabel = new Label(config.getDb() == null ? "(not initialized)" : config.getDb().sqlitePath());

        grid.add(new Label("Server Port:"), 0, 0);
        grid.add(portField, 1, 0);
        grid.add(new Label("Server root (VFS):"), 0, 1);
        grid.add(pathField, 1, 1);
        grid.add(new Label("Admin Username:"), 0, 2);
        grid.add(adminUserField, 1, 2);
        grid.add(new Label("Admin Password:"), 0, 3);
        grid.add(adminPassField, 1, 3);

        grid.add(new Label("SQLite DB file:"), 0, 4);
        grid.add(sqlitePathLabel, 1, 4);

        Button saveBtn = new Button("Save Configuration");
        saveBtn.setOnAction(e -> {
            
            try {
                int port = Integer.parseInt(portField.getText());
                if (port < 1024 || port > 65535) {
                    showErrorBubble("Port must be between 1024 and 65535");
                    return;
                }
            } catch (NumberFormatException ex) {
                showErrorBubble("Invalid port number");
                return;
            }

            
            if (pathField.getText().trim().isEmpty()) {
                showErrorBubble("Server root path cannot be empty");
                return;
            }

            
            if (!adminUserField.getText().isEmpty() && adminUserField.getText().length() < 3) {
                showErrorBubble("Username must be at least 3 characters");
                return;
            }

            
            if (!adminPassField.getText().isEmpty() && adminPassField.getText().length() < 3) {
                showErrorBubble("Password must be at least 3 characters");
                return;
            }

            try {
                config.setPort(Integer.parseInt(portField.getText()));
                config.setResourcesPath(pathField.getText());
                if (!adminUserField.getText().isEmpty()) {
                    config.setAdminUser(adminUserField.getText());
                }
                if (!adminPassField.getText().isEmpty()) {
                    config.setAdminPass(adminPassField.getText());
                }
                config.save();
                statusLabel.setText("Configuration saved and Database initialized.");
                dbManager = new DatabaseManager(config);
                dbManager.initialize();
                sqlitePathLabel.setText(config.getDb() == null ? "(not initialized)" : config.getDb().sqlitePath());
                refreshUserList();
                showSuccessBubble("Configuration saved successfully");
            } catch (Exception ex) {
                showErrorBubble("Error saving config: " + ex.getMessage());
            }
        });

        HBox btnBox = new HBox(10, saveBtn);
        grid.add(btnBox, 1, 5);
        
        Button resetBtn = new Button("Reset System");
        resetBtn.setOnAction(e -> showResetSystemDialog());
        grid.add(resetBtn, 1, 6);

        tab.setContent(grid);
        return tab;
    }

    private Tab createControlTab() {
        Tab tab = new Tab("Server Control");
        tab.setClosable(false);

        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        serverStatusLabel = new Label("Server Status: Unknown");
        serverStatusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox btnRow = new HBox(10);
        Button startBtn = new Button("Start DFS Server");
        startBtn.setPrefWidth(180);
        startBtn.setOnAction(e -> startServer());

        Button stopBtn = new Button("Stop DFS Server");
        stopBtn.setPrefWidth(180);
        stopBtn.setOnAction(e -> stopServer());

        btnRow.getChildren().addAll(startBtn, stopBtn);

        serverLog = new TextArea();
        serverLog.setEditable(false);
        serverLog.setWrapText(true);
        serverLog.setPrefHeight(400);
        serverLog.getStyleClass().add("log-area");

        vbox.getChildren().addAll(serverStatusLabel, btnRow, new Label("Server Log:"), serverLog);
        tab.setContent(vbox);
        return tab;
    }

    private Tab createUsersTab() {
        Tab tab = new Tab("User Management");
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                refreshUserList();
            }
        });

        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        userListView = new ListView<>();
        pane.setCenter(new VBox(5, new Label("Registered Users:"), userListView));

        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(0, 0, 0, 10));
        Button refreshBtn = new Button("Refresh Users");
        refreshBtn.setOnAction(e -> refreshUserList());

        Button changeRoleBtn = new Button("Change Role");
        changeRoleBtn.setOnAction(e -> showChangeRoleDialog());
        
        Button deleteUserBtn = new Button("Delete User");
        deleteUserBtn.setOnAction(e -> showDeleteUserDialog());
        
        Button renameUserBtn = new Button("Rename User");
        renameUserBtn.setOnAction(e -> showRenameUserDialog());

        rightBox.getChildren().addAll(refreshBtn, changeRoleBtn, renameUserBtn, deleteUserBtn);
        pane.setRight(rightBox);

        tab.setContent(pane);
        return tab;
    }

    private Tab createRolesTab() {
        Tab tab = new Tab("Roles");
        tab.setClosable(false);

        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(10));

        ListView<String> rolesList = new ListView<>();
        TextField roleName = new TextField();
        
        ObservableList<String> allCommands = FXCollections.observableArrayList(
                "upload","download","list","myfiles","pwd","cd","delete","mkdir","whoami","help","refresh","quit","exit","listclients","clientinfo",
                
                "listroles","createrole","deleterole","shutdown","kick","setrole"
        );
        Map<String, BooleanProperty> commandSelection = new LinkedHashMap<>();
        for (String c : allCommands) commandSelection.put(c, new SimpleBooleanProperty(false));
        ListView<String> commandsListView = new ListView<>(allCommands);
        commandsListView.setCellFactory(CheckBoxListCell.forListView(item -> commandSelection.get(item)));

         Button refreshRoles = new Button("Refresh Roles");
         Button saveRole = new Button("Create/Update Role");
         Button deleteRole = new Button("Delete Role");

         refreshRoles.setOnAction(e -> {
             try {
                 if (dbManager == null) dbManager = new DatabaseManager(config);
                 dbManager.initialize();
                 rolesList.getItems().clear();
                 rolesList.getItems().addAll(dbManager.getAllRoles());
             } catch (Exception ex) {
                 statusLabel.setText("Error loading roles: " + ex.getMessage());
             }
         });

         saveRole.setOnAction(e -> {
             try {
                 String rn = roleName.getText().trim();
                if (rn.isEmpty()) {
                    showErrorBubble("Role name required.");
                    return;
                }
                if (BUILTIN_ROLES.contains(rn)) {
                    showErrorBubble("Cannot create built-in role: " + rn);
                    return;
                }
                
                List<String> selected = new ArrayList<>();
                for (Map.Entry<String, BooleanProperty> entry : commandSelection.entrySet()) {
                    if (entry.getValue().get()) selected.add(entry.getKey());
                }
                if (selected.isEmpty()) {
                    showErrorBubble("Select at least one command for the role.");
                    return;
                }
                String cmds = String.join(",", selected);
                 if (dbManager == null) dbManager = new DatabaseManager(config);
                 dbManager.initialize();
                 
                 if (dbManager.getRoleCommands(rn) != null) {
                     showErrorBubble("Role '" + rn + "' already exists.");
                     return;
                 }
                 dbManager.createOrUpdateRole(rn, cmds);
                 refreshRoles.fire();
                 statusLabel.setText("Role saved: " + rn);
             } catch (Exception ex) {
                 showErrorBubble("Error saving role: " + ex.getMessage());
             }
         });

         deleteRole.setOnAction(e -> {
             try {
                 String sel = rolesList.getSelectionModel().getSelectedItem();
                 if (sel == null) {
                     showErrorBubble("Select a role to delete.");
                     return;
                 }
                 if (BUILTIN_ROLES.contains(sel)) {
                     showErrorBubble("Cannot delete built-in role: " + sel);
                     return;
                 }
                 Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete role '" + sel + "'? This cannot be undone.", ButtonType.YES, ButtonType.NO);
                 confirm.setHeaderText(null);
                 confirm.showAndWait().ifPresent(btn -> {
                     if (btn == ButtonType.YES) {
                         try {
                             if (dbManager == null) dbManager = new DatabaseManager(config);
                             dbManager.initialize();
                             dbManager.deleteRole(sel);
                             refreshRoles.fire();
                             statusLabel.setText("Deleted role: " + sel);
                         } catch (Exception ex) {
                             showErrorBubble("Error deleting role: " + ex.getMessage());
                         }
                     }
                 });
             } catch (Exception ex) {
                 showErrorBubble("Error deleting role: " + ex.getMessage());
             }
         });

         rolesList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
             if (newV == null) return;
             try {
                 if (dbManager == null) dbManager = new DatabaseManager(config);
                 dbManager.initialize();
                 roleName.setText(newV);
                String cmds = dbManager.getRoleCommands(newV);
                
                for (Map.Entry<String, BooleanProperty> entry : commandSelection.entrySet()) entry.getValue().set(false);
                if (cmds != null && !cmds.trim().isEmpty()) {
                    String[] parts = cmds.split(",");
                    for (String p : parts) {
                        String t = p.trim();
                        if (commandSelection.containsKey(t)) commandSelection.get(t).set(true);
                    }
                }
             } catch (Exception ex) {
                 statusLabel.setText("Error loading role: " + ex.getMessage());
             }
         });

         VBox rightBox = new VBox(8, new Label("Role Name:"), roleName, new Label("Commands (tick to allow):"), commandsListView, new HBox(8, saveRole, deleteRole), refreshRoles);
         rightBox.setPadding(new Insets(10));
         
         Label builtinLabel = new Label("Built-in roles: admin, guest");
         builtinLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

         pane.setLeft(new VBox(5, new Label("Available Roles:"), rolesList, builtinLabel));
         pane.setCenter(rightBox);

         tab.setContent(pane);
         return tab;
     }
    private Tab createHealthTab() {
        Tab tab = new Tab("Server Health");
        tab.setClosable(false);
        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        connLabel = new Label("Active Connections: 0");
        memLabel = new Label("JVM Memory: 0 MB");
        sysMemLabel = new Label("System Memory: 0 MB / 0 MB");
        cpuLabel = new Label("CPU Load: 0%");
        threadLabel = new Label("Active Threads: 0");
        dbLabel = new Label("DB Size: 0 MB");
        uptimeLabel = new Label("Uptime: 0s");

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(true);
        healthChart = new LineChart<>(xAxis, yAxis);
        healthChart.setTitle("Server Resource Usage");
        healthChart.setAnimated(false);
        healthChart.setCreateSymbols(false);
        healthChart.getData().clear();

        
        ComboBox<String> metricSelector = new ComboBox<>(FXCollections.observableArrayList("CPU %", "Memory MB", "Threads", "DB Size MB"));
        metricSelector.setValue("CPU %");
        metricSelector.setOnAction(e -> {
            currentMetric = metricSelector.getValue();
            updateHealthChart();
        });

        
        ComboBox<String> rangeSelector = new ComboBox<>(FXCollections.observableArrayList("Day", "Week", "Month", "Year"));
        rangeSelector.setValue("Day");
        rangeSelector.setOnAction(e -> {
            currentRange = rangeSelector.getValue();
            updateHealthChart();
        });

        vbox.getChildren().addAll(connLabel, memLabel, sysMemLabel, cpuLabel, threadLabel, dbLabel, uptimeLabel, new Label("Metric:"), metricSelector, new Label("Time Range:"), rangeSelector, healthChart);
        tab.setContent(vbox);
        return tab;
    }

    private void startHealthMonitor() {
        stopHealthMonitor();
        healthTimer = new Timer(true);
        healthTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> updateHealth());
            }
        }, 0, 2000);
    }
    private void stopHealthMonitor() {
        if (healthTimer != null) {
            healthTimer.cancel();
            healthTimer = null;
        }
    }
    private void updateHealth() {
        try {
            int activeConns = getActiveConnections();

            
            long memUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            double memMB = memUsed / (1024.0 * 1024.0);
            double maxMemMB = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);

            
            OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
            long totalSysMem = 0;
            long freeSysMem = 0;
            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOsMXBean) {
                totalSysMem = sunOsMXBean.getTotalMemorySize();
                freeSysMem = sunOsMXBean.getFreeMemorySize();
            }
            double totalSysMemMB = totalSysMem / (1024.0 * 1024.0);
            double usedSysMemMB = (totalSysMem - freeSysMem) / (1024.0 * 1024.0);

            double cpuLoad = getCpuLoad();
            int threads = getThreadCount();
            long dbSize = getDbSize();
            double dbMB = dbSize / (1024.0 * 1024.0);
            long t = (System.currentTimeMillis() - healthStartTime) / 1000;

            
            long uptimeSec = t;
            String uptimeStr = String.format("%dd %dh %dm %ds",
                    uptimeSec / 86400,
                    (uptimeSec % 86400) / 3600,
                    (uptimeSec % 3600) / 60,
                    uptimeSec % 60);

            if (connLabel != null) connLabel.setText("Active Connections: " + activeConns);
            if (memLabel != null) memLabel.setText(String.format("JVM Memory: %.1f / %.1f MB", memMB, maxMemMB));
            if (sysMemLabel != null) sysMemLabel.setText(String.format("System Memory: %.1f / %.1f MB", usedSysMemMB, totalSysMemMB));
            if (cpuLabel != null) cpuLabel.setText(String.format("CPU Load: %.1f%%", cpuLoad * 100));
            if (threadLabel != null) threadLabel.setText("Active Threads: " + threads);
            if (dbLabel != null) dbLabel.setText(String.format("DB Size: %.1f MB", dbMB));
            if (uptimeLabel != null) uptimeLabel.setText("Uptime: " + uptimeStr);

            addHealthHistory("CPU %", t, cpuLoad * 100);
            addHealthHistory("Memory MB", t, memMB);
            addHealthHistory("Threads", t, threads);
            addHealthHistory("DB Size MB", t, dbMB);
            if (healthChart != null) updateHealthChart();
        } catch (Exception e) {
            
        }
    }
    private void addHealthHistory(String metric, long t, double value) {
        healthHistory.putIfAbsent(metric, new ArrayList<>());
        List<HealthDataPoint> list = healthHistory.get(metric);
        list.add(new HealthDataPoint(t, value));
        if (list.size() > MAX_HISTORY_POINTS) list.remove(0);
    }
    private void updateHealthChart() {
        if (healthChart == null) return;
        healthChart.getData().clear();
        List<HealthDataPoint> allData = healthHistory.getOrDefault(currentMetric, new ArrayList<>());

        long maxTimeRange = switch (currentRange) {
            case "Day" -> 24 * 60 * 60;
            case "Week" -> 7 * 24 * 60 * 60;
            case "Month" -> 30 * 24 * 60 * 60;
            case "Year" -> 365 * 24 * 60 * 60;
            default -> 24 * 60 * 60;
        };

        long currentTime = (System.currentTimeMillis() - healthStartTime) / 1000;
        long minTime = Math.max(0, currentTime - maxTimeRange);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(currentMetric);
        for (HealthDataPoint dp : allData) {
            if (dp.time >= minTime) series.getData().add(new XYChart.Data<>(dp.time, dp.value));
        }

        healthChart.getData().add(series);

        if (healthChart.getXAxis() instanceof NumberAxis xAxis) {
            xAxis.setAutoRanging(false);
            if (series.getData().isEmpty()) {
                xAxis.setLowerBound(0);
                xAxis.setUpperBound(maxTimeRange);
            } else {
                xAxis.setLowerBound(minTime);
                xAxis.setUpperBound(currentTime);
                double tickUnit = switch (currentRange) {
                    case "Day" -> 3600;
                    case "Week" -> 86400;
                    case "Month" -> 259200;
                    case "Year" -> 2592000;
                    default -> 3600;
                };
                xAxis.setTickUnit(tickUnit);
            }
        }
    }
    private void saveHealthHistory() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("startTime", healthStartTime);
            data.put("history", healthHistory);
            new ObjectMapper().writeValue(new java.io.File(HEALTH_HISTORY_FILE), data);
        } catch (Exception e) {
            System.err.println("Failed to save health history: " + e.getMessage());
        }
    }
    private void loadHealthHistory() {
        try {
            java.io.File file = new java.io.File(HEALTH_HISTORY_FILE);
            if (!file.exists()) return;
            Map<String, Object> data = new ObjectMapper().readValue(file, new TypeReference<Map<String, Object>>(){});
            if (data != null) {
                healthStartTime = ((Number) data.get("startTime")).longValue();
                Map<String, List<Map<String, Number>>> rawHistory = (Map<String, List<Map<String, Number>>>) data.get("history");
                for (Map.Entry<String, List<Map<String, Number>>> entry : rawHistory.entrySet()) {
                    List<HealthDataPoint> points = new ArrayList<>();
                    for (Map<String, Number> point : entry.getValue()) {
                        points.add(new HealthDataPoint(point.get("time").longValue(), point.get("value").doubleValue()));
                    }
                    healthHistory.put(entry.getKey(), points);
                }
            }
        } catch (Exception e) {
            
        }
    }

    private void startServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            statusLabel.setText("Server is already running.");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-Dexec.mainClass=org.example.server.DFSServer", "exec:java");
            pb.redirectErrorStream(true);
            serverProcess = pb.start();
            statusLabel.setText("Server started.");
            serverStatusLabel.setText("Server Status: RUNNING");

            
            exec.submit(() -> streamToLog(serverProcess.getInputStream()));
        } catch (Exception e) {
            statusLabel.setText("Failed to start server: " + e.getMessage());
            serverStatusLabel.setText("Server Status: ERROR");
        }
    }

    private void streamToLog(InputStream is) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String l = line;
                Platform.runLater(() -> serverLog.appendText(l + "\n"));
            }
        } catch (Exception ignored) {}
    }

    private void stopServer() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Stop the DFS server?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;

            
            boolean graceful = false;
            if (config.getAdminUser() != null && config.getAdminPass() != null) {
                try {
                    graceful = tryGracefulShutdown();
                } catch (Exception e) {
                    
                    graceful = false;
                }
            }

            if (graceful) {
                statusLabel.setText("Server stopped (graceful).");
                serverStatusLabel.setText("Server Status: STOPPED");
                return;
            }

        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor();
            } catch (InterruptedException ignored) {}
            if (serverProcess.isAlive()) {
                serverProcess.destroyForcibly();
            }
            statusLabel.setText("Server stopped.");
            serverStatusLabel.setText("Server Status: STOPPED");
        } else {
            statusLabel.setText("Server not running.");
        }
        });
    }

    private boolean tryGracefulShutdown() {
        
        try (Socket s = new Socket("127.0.0.1", config.getPort())) {
            s.setSoTimeout(3000);
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));

            
            long end = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < end && br.ready()) br.readLine();

            
            String loginCmd = String.format("login %s,%s", config.getAdminUser(), config.getAdminPass());
            bw.write(loginCmd + "\n"); bw.flush();

            
            String line;
            boolean loggedIn = false;
            end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end && (line = br.readLine()) != null) {
                if (line.toLowerCase().startsWith("success")) { loggedIn = true; break; }
                if (line.toLowerCase().startsWith("error")) break;
            }

            if (!loggedIn) return false;

            
            bw.write("shutdown\n"); bw.flush();
            
            end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end && (line = br.readLine()) != null) {
                if (line.toLowerCase().contains("shutting down") || line.toLowerCase().contains("success")) {
                    return true;
                }
            }
            return true; 
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshUserList() {
        if (dbManager == null) {
            dbManager = new DatabaseManager(config);
            try {
                dbManager.initialize();
            } catch (SQLException e) {
                statusLabel.setText("DB Init failed: " + e.getMessage());
                return;
            }
        }
        try {
            List<User> users = dbManager.getAllUsers();
            userListView.getItems().clear();
            for (User user : users) {
                userListView.getItems().add(user.username() + " [" + user.role() + "]");
            }
        } catch (SQLException e) {
            statusLabel.setText("Error refreshing users: " + e.getMessage());
        }
    }

    private void showChangeRoleDialog() {
        String selected = userListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String username = selected.split(" ")[0];

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Change Role");
        dialog.setHeaderText("Change role for user: " + username);
        dialog.setContentText("Enter new role:");

        dialog.showAndWait().ifPresent(newRole -> {
            try {
                if (dbManager.updateUserRole(username, newRole)) {
                    refreshUserList();
                    statusLabel.setText("Role updated for " + username);
                }
            } catch (SQLException e) {
                statusLabel.setText("Error updating role: " + e.getMessage());
            }
        });
    }
    
    private void showDeleteUserDialog() {
        String selected = userListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorBubble("Select a user to delete");
            return;
        }

        String username = selected.split(" ")[0];
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "Delete user '" + username + "'? This cannot be undone.", 
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    if (dbManager.deleteUser(username)) {
                        refreshUserList();
                        showSuccessBubble("User deleted: " + username);
                    } else {
                        showErrorBubble("Failed to delete user");
                    }
                } catch (SQLException e) {
                    showErrorBubble("Error deleting user: " + e.getMessage());
                }
            }
        });
    }
    
    private void showRenameUserDialog() {
        String selected = userListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorBubble("Select a user to rename");
            return;
        }

        String oldUsername = selected.split(" ")[0];
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Rename User");
        dialog.setHeaderText("Rename user: " + oldUsername);
        dialog.setContentText("Enter new username:");

        dialog.showAndWait().ifPresent(newUsername -> {
            if (newUsername.trim().isEmpty() || newUsername.length() < 3) {
                showErrorBubble("Username must be at least 3 characters");
                return;
            }
            try {
                if (dbManager.renameUser(oldUsername, newUsername.trim())) {
                    refreshUserList();
                    showSuccessBubble("User renamed: " + oldUsername + " -> " + newUsername);
                } else {
                    showErrorBubble("Failed to rename user (may already exist)");
                }
            } catch (SQLException e) {
                showErrorBubble("Error renaming user: " + e.getMessage());
            }
        });
    }
    
    private void showResetSystemDialog() {
        Alert confirm = new Alert(Alert.AlertType.WARNING, 
            "Reset system? This will:\n" +
            "- Delete all users\n" +
            "- Delete all roles\n" +
            "- Delete all files in VFS\n" +
            "- Reset metadata\n" +
            "- Clear health history\n\n" +
            "This CANNOT be undone!", 
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("WARNING: System Reset");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    if (dbManager != null) {
                        dbManager.resetDatabase();
                    }
                    
                    Path vfsRoot = Path.of(config.getResourcesPath());
                    if (Files.exists(vfsRoot)) {
                        Files.walk(vfsRoot)
                            .filter(p -> !p.equals(vfsRoot))
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                    }
                    
                    Path metadataFile = vfsRoot.resolve("server_metadata.json");
                    if (Files.exists(metadataFile)) {
                        Files.writeString(metadataFile, "{}");
                    }
                    
                    healthHistory.clear();
                    java.io.File healthFile = new java.io.File(HEALTH_HISTORY_FILE);
                    if (healthFile.exists()) healthFile.delete();
                    
                    refreshUserList();
                    showSuccessBubble("System reset complete");
                } catch (Exception e) {
                    showErrorBubble("Error resetting system: " + e.getMessage());
                }
            }
        });
    }

    private Tab createConnectionLogsTab() {
        Tab tab = new Tab("Connection Logs");
        tab.setClosable(false);

        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(20));

        connectionLog = new TextArea();
        connectionLog.setEditable(false);
        connectionLog.setWrapText(true);
        connectionLog.setPrefHeight(500);
        connectionLog.getStyleClass().add("log-area");
        connectionLog.appendText("Connection Log Started\n");
        connectionLog.appendText("===================\n\n");

        Button clearBtn = new Button("Clear Log");
        clearBtn.setOnAction(e -> {
            connectionLog.clear();
            connectionLog.appendText("Log cleared at " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n\n");
        });

        Button refreshBtn = new Button("Refresh Connections");
        refreshBtn.setOnAction(e -> refreshConnectionLog());

        HBox buttonBox = new HBox(10, refreshBtn, clearBtn);
        vbox.getChildren().addAll(new Label("Active Connections and Recent Activity:"), buttonBox, connectionLog);

        tab.setContent(vbox);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) startConnectionLogMonitor();
            else stopConnectionLogMonitor();
        });

        return tab;
    }

    private void startConnectionLogMonitor() {
        if (connectionLogTimer != null) connectionLogTimer.cancel();
        connectionLogTimer = new Timer(true);
        connectionLogTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> refreshConnectionLog());
            }
        }, 0, 5000); 
    }

    private void stopConnectionLogMonitor() {
        if (connectionLogTimer != null) {
            connectionLogTimer.cancel();
            connectionLogTimer = null;
        }
    }

    private void refreshConnectionLog() {
        if (connectionLog == null) return;

        
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        
        String currentText = connectionLog.getText();
        String[] lines = currentText.split("\n");
        if (lines.length > 100) {
            
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 80; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            connectionLog.setText(sb.toString());
        }
    }

    private int getActiveConnections() {
        
        
        return 0;
    }
    private double getCpuLoad() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        try {
            java.lang.reflect.Method m = os.getClass().getMethod("getSystemCpuLoad");
            Object v = m.invoke(os);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {}
        return 0;
    }
    private int getThreadCount() {
        ThreadMXBean tm = ManagementFactory.getThreadMXBean();
        return tm.getThreadCount();
    }
    private long getDbSize() {
        try {
            if (config.getDb() != null && config.getDb().sqlitePath() != null) {
                return Files.size(Path.of(config.getDb().sqlitePath()));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
