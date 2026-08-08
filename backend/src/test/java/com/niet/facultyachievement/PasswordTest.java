package com.niet.facultyachievement;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    @Test
    public void generateHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("Password@123");
        System.out.println("GEN_HASH=" + hash);
        System.out.println("MATCHES=" + encoder.matches("Password@123", hash));
    }
}
