package com.savewise.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/** With SPRING_PROFILES_ACTIVE=prod the app refuses to start with a guessable token-signing key. */
@Component
@Profile("prod")
public class ProductionSafetyCheck {
    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);
    private static final Set<String> COMMON_DB_PASSWORDS = Set.of("postgres", "password", "admin", "root", "savewise");

    public ProductionSafetyCheck(@Value("${app.jwt.secret}") String jwtSecret,
                                 @Value("${app.cookie.secure}") boolean cookieSecure,
                                 @Value("${spring.datasource.password}") String dbPassword) {
        if (jwtSecret.length() < 32 || jwtSecret.toLowerCase().contains("change-me")) {
            throw new IllegalStateException("JWT_SECRET must be a random value of at least 32 characters. "
                    + "Run scripts/generate-secrets to create one.");
        }
        if (!cookieSecure) {
            log.warn("COOKIE_SECURE is false: login cookies will also travel over plain HTTP. Only acceptable for local testing.");
        }
        if (dbPassword.length() < 12 || COMMON_DB_PASSWORDS.contains(dbPassword.toLowerCase())) {
            log.warn("The database password is short or common. Use a random one of 20+ characters in production.");
        }
    }
}
