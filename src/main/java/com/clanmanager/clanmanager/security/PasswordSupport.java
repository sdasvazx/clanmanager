package com.clanmanager.clanmanager.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordSupport {

    private static final String BCRYPT_PREFIX = "$2";
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordSupport() {
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(requirePassword(rawPassword));
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        String password = rawPassword == null ? "" : rawPassword;
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isEncoded(storedPassword)) {
            return ENCODER.matches(password, storedPassword);
        }
        return storedPassword.equals(password);
    }

    public static boolean isEncoded(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(BCRYPT_PREFIX);
    }

    private static String requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해 주세요.");
        }
        return rawPassword.trim();
    }
}
