package org.example;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class AuthDialog extends Dialog<AuthResult> {

    public AuthDialog() {
        setTitle("Authentication");
        setHeaderText("Connect to Distributed File System");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        
        Tab loginTab = new Tab("Login");
        loginTab.setContent(createLoginPane());

        
        Tab signupTab = new Tab("Sign Up");
        signupTab.setContent(createSignupPane());

        
        Tab guestTab = new Tab("Guest");
        guestTab.setContent(createGuestPane());

        tabPane.getTabs().addAll(loginTab, signupTab, guestTab);

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
                return getAuthResult(selectedTab);
            }
            return null;
        });
    }

    private TextField loginUsername;
    private PasswordField loginPassword;

    private TextField signupUsername;
    private PasswordField signupPassword;
    private PasswordField signupConfirm;

    private VBox createLoginPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        loginUsername = new TextField();
        loginUsername.setPromptText("Username");
        loginPassword = new PasswordField();
        loginPassword.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(loginUsername, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(loginPassword, 1, 1);

        vbox.getChildren().add(grid);
        return vbox;
    }

    private VBox createSignupPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        signupUsername = new TextField();
        signupUsername.setPromptText("Username");
        signupPassword = new PasswordField();
        signupPassword.setPromptText("Password");
        signupConfirm = new PasswordField();
        signupConfirm.setPromptText("Confirm Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(signupUsername, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(signupPassword, 1, 1);
        grid.add(new Label("Confirm:"), 0, 2);
        grid.add(signupConfirm, 1, 2);

        Label infoLabel = new Label("New users will be assigned 'guest' role by default.");
        infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        vbox.getChildren().addAll(grid, infoLabel);
        return vbox;
    }

    private VBox createGuestPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label info = new Label("Connect as a guest with limited permissions.");
        info.setWrapText(true);

        Label permissions = new Label("Guest permissions:\n• List files\n• View current directory\n• Get help");
        permissions.setStyle("-fx-font-size: 11px;");

        vbox.getChildren().addAll(info, permissions);
        return vbox;
    }

    private AuthResult getAuthResult(Tab selectedTab) {
        String tabText = selectedTab.getText();

        switch (tabText) {
            case "Login":
                String loginUser = loginUsername.getText().trim();
                String loginPass = loginPassword.getText();

                if (loginUser.isEmpty() || loginPass.isEmpty()) {
                    showError("Username and password are required.");
                    return null;
                }

                return new AuthResult(AuthType.LOGIN, loginUser, loginPass);

            case "Sign Up":
                String signupUser = signupUsername.getText().trim();
                String signupPass = signupPassword.getText();
                String confirmPass = signupConfirm.getText();

                if (signupUser.isEmpty() || signupPass.isEmpty()) {
                    showError("Username and password are required.");
                    return null;
                }

                if (signupUser.length() < 3) {
                    showError("Username must be at least 3 characters.");
                    return null;
                }

                if (signupPass.length() < 4) {
                    showError("Password must be at least 4 characters.");
                    return null;
                }

                if (!signupPass.equals(confirmPass)) {
                    showError("Passwords do not match.");
                    return null;
                }

                return new AuthResult(AuthType.SIGNUP, signupUser, signupPass);

            case "Guest":
                return new AuthResult(AuthType.GUEST, null, null);

            default:
                return null;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
