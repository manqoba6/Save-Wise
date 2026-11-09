package com.savewise.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token lives only in this cookie: HttpOnly (scripts cannot read it, so an XSS bug cannot steal it),
 * SameSite=Strict (never sent from another site), Secure when served over HTTPS, and only sent to /api/auth.
 */
@Component
public class RefreshCookie {
    public static final String NAME = "sw_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshCookie(@Value("${app.cookie.secure}") boolean secure,
                         @Value("${app.cookie.same-site}") String sameSite,
                         @Value("${app.jwt.refresh-expiration-ms}") long refreshMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofMillis(refreshMs);
    }

    public ResponseCookie issue(String token) {
        return build(token, maxAge);
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(secure).sameSite(sameSite).path(PATH).maxAge(age).build();
    }
}
