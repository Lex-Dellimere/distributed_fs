package org.example;

/**
 * Result of authentication dialog
 */
public record AuthResult(AuthType type, String username, String password) {}
