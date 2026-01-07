package org.example;

public record AuthResult(AuthType type, String username, String password) {}
