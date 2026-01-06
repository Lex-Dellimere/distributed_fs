module org.example.server {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires org.xerial.sqlitejdbc;
    requires com.fasterxml.jackson.databind;
    requires jbcrypt;
    requires java.management;
    requires jdk.management;

    exports org.example.server;
    exports org.example.client;
    exports org.example.vfs;
    exports org.example.config;
    exports org.example.db;
    exports org.example.manager;
    exports org.example.setup;

    opens org.example.server to javafx.fxml;
    opens org.example.client to javafx.fxml;
    opens org.example.vfs to javafx.fxml;
    opens org.example.config to javafx.fxml;
    opens org.example.db to javafx.fxml;
    opens org.example.manager to javafx.fxml;
    opens org.example.setup to javafx.fxml;
}
