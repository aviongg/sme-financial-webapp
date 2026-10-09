package com.app.sme_health_backend.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${app.security.argon2.salt-length:16}") int saltLength,
            @Value("${app.security.argon2.hash-length:32}") int hashLength,
            @Value("${app.security.argon2.parallelism:1}") int parallelism,
            @Value("${app.security.argon2.memory:65536}") int memory,
            @Value("${app.security.argon2.iterations:3}") int iterations
    ) {
        return new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations);
    }
}
