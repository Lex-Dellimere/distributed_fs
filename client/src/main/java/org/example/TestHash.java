package org.example;

import org.mindrot.jbcrypt.BCrypt;

public class TestHash {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: TestHash <password>");
            System.exit(1);
        }
        String password = args[0];
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println(hash);
    }
}

