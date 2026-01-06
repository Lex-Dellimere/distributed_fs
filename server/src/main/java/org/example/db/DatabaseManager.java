package org.example.db;

import org.example.config.ServerConfig;
import org.example.config.DatabaseConfig;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DatabaseManager {
    private final ServerConfig config;
    private Connection connection;

    public DatabaseManager(ServerConfig config) {
        this.config = config;
    }

    /**
     * Initializes the database, creating tables and default roles if needed.
     * Uses SQLite as the SQL dialect.
     *
     * @throws SQLException if a database error occurs
     */
    public void initialize() throws SQLException {
        // Determine SQLite database path
        String sqlitePath = null;

        if (config.getDb() != null && config.getDb().sqlitePath() != null && !config.getDb().sqlitePath().trim().isEmpty()) {
            sqlitePath = config.getDb().sqlitePath().trim();
        }

        // If no path configured or file doesn't exist, search for existing database
        if (sqlitePath == null || !Files.exists(Path.of(sqlitePath))) {
            // Try to find existing database
            List<String> candidates = Arrays.asList(
                "dfs_database.db",
                "freedrs.db",
                "data/dfs_database.db",
                "data/freedrs.db",
                "./dfs_database.db"
            );

            for (String candidate : candidates) {
                if (Files.exists(Path.of(candidate))) {
                    sqlitePath = candidate;
                    System.out.println("Found existing database: " + sqlitePath);
                    break;
                }
            }

            // If no database found, create new one
            if (sqlitePath == null) {
                sqlitePath = "dfs_database.db";
                System.out.println("Creating new database: " + sqlitePath);
            }

            // Update config with resolved path
            config.setDb(new DatabaseConfig(sqlitePath));
            config.save();
        } else {
            System.out.println("Using configured database: " + sqlitePath);
        }

        // SQLite will auto-create the database file if it doesn't exist
        connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);

        // Set proper file permissions on newly created database (POSIX systems only)
        try {
            Path dbPath = Path.of(sqlitePath);
            if (Files.exists(dbPath)) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(dbPath, perms);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // ignore on non-POSIX systems or if permissions can't be set
        }

        try (Statement stmt = connection.createStatement()) {
            // Users table
            String idCol = "INTEGER PRIMARY KEY AUTOINCREMENT";
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id " + idCol + "," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password_hash VARCHAR(100) NOT NULL," +
                    "role VARCHAR(50) NOT NULL" +
                    ")");

            // Roles/permissions table: name (unique), commands (comma-separated)
            stmt.execute("CREATE TABLE IF NOT EXISTS roles (" +
                    "name VARCHAR(50) UNIQUE NOT NULL," +
                    "commands TEXT NOT NULL" +
                    ")");

            // Seed only admin and guest roles
            if (getRoleCommands("admin") == null) {
                createOrUpdateRole("admin", "*");
            }
            if (getRoleCommands("guest") == null) {
                createOrUpdateRole("guest", String.join(",",
                        Arrays.asList("list","pwd","cd","help","whoami","quit","exit")
                ));
            }
        }
    }

    /**
     * Registers a new user with the given username, password, and role.
     *
     * @param username the username
     * @param password the password
     * @param role     the role
     * @return true if registration succeeded, false if username exists
     * @throws SQLException if a database error occurs
     */
    public boolean registerUser(String username, String password, String role) throws SQLException {
        // Check for existing username
        if (getUserByUsername(username) != null) {
            return false; // duplicate
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hash);
            pstmt.setString(3, role);
            return pstmt.executeUpdate() > 0;
        }
    }

    private User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, role FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
            }
        }
        return null;
    }

    public User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, password_hash, role FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("password_hash");
                if (BCrypt.checkpw(password, hash)) {
                    return new User(rs.getInt("id"), username, rs.getString("role"));
                }
            }
        }
        return null;
    }

    public boolean updateUserRole(String username, String newRole) throws SQLException {
        String sql = "UPDATE users SET role = ? WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newRole);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Returns a list of all users in the database.
     *
     * @return list of users
     * @throws SQLException if a database error occurs
     */
    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT id, username, role FROM users";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("role")));
            }
            return users;
        }
    }

    // Role management
    public void createOrUpdateRole(String roleName, String commaSeparatedCommands) throws SQLException {
        String existing = getRoleCommands(roleName);
        if (existing == null) {
            String sql = "INSERT INTO roles (name, commands) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, roleName);
                pstmt.setString(2, commaSeparatedCommands);
                pstmt.executeUpdate();
            }
        } else {
            String sql = "UPDATE roles SET commands = ? WHERE name = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, commaSeparatedCommands);
                pstmt.setString(2, roleName);
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * Returns a list of all roles in the database.
     *
     * @return list of role names
     * @throws SQLException if a database error occurs
     */
    public List<String> getAllRoles() throws SQLException {
        String sql = "SELECT name FROM roles";
        List<String> roles = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                roles.add(rs.getString("name"));
            }
        }
        return roles;
    }

    public String getRoleCommands(String roleName) throws SQLException {
        String sql = "SELECT commands FROM roles WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roleName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("commands");
            }
        }
        return null;
    }

    public void deleteRole(String roleName) throws SQLException {
        String sql = "DELETE FROM roles WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, roleName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Checks if a command is NOT allowed for a given role.
     *
     * @param roleName the role name
     * @param command  the command
     * @return true if forbidden, false if allowed
     * @throws SQLException if a database error occurs
     */
    public boolean isCommandForbidden(String roleName, String command) throws SQLException {
        String commands = getRoleCommands(roleName);
        if (commands == null) {
            // If role not found, treat as restrictive: only allow a small set (fallback)
            return !List.of("list","pwd","cd","help","whoami","quit","exit").contains(command);
        }
        if (commands.trim().equals("*")) return false;
        List<String> allowed = Arrays.stream(commands.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return !allowed.contains(command);
    }
}
