package com.example.Auth.Tool;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordHashTool {
    private PasswordHashTool() {}
    public static void main(String[] args) {
        if (args.length != 1 || args[0].length() < 8 || args[0].length() > 72) {
            throw new IllegalArgumentException("Provide one password containing 8 to 72 characters.");
        }
        System.out.println(new BCryptPasswordEncoder(12).encode(args[0]));
    }
}
