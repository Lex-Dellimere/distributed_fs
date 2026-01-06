package org.example.db;

/**
 * Immutable user record for database users.
 */
public record User(int id, String username, String role) {}
