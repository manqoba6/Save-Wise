package com.savewise.service;

import com.savewise.dto.*;
import com.savewise.entity.RefreshToken;
import com.savewise.entity.Role;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.RefreshTokenRepository;
import com.savewise.repository.RoleRepository;
import com.savewise.repository.UserRepository;
import com.savewise.security.JwtService;
import com.savewise.util.Tokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthenticationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final long refreshExpirationMs;

    public AuthenticationService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                                 PasswordEncoder encoder, AuthenticationManager authManager, JwtService jwt,
                                 @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public AuthResult register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        Role role = roles.findByRoleName("USER")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "USER role is not seeded."));
        User user = User.builder()
                .role(role)
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .email(email)
                .password(encoder.encode(req.password()))
                .build();
        users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));
        return issueTokens(user);
    }

    /** Swaps the refresh token from the cookie for a new access token and a new refresh token (each works once). */
    @Transactional
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No active session.");
        }
        RefreshToken stored = refreshTokens.findByToken(Tokens.sha256(refreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token."));
        if (stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account has been disabled.");
        }
        refreshTokens.delete(stored); // rotate: each refresh token is single-use
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.deleteByToken(Tokens.sha256(refreshToken));
        }
    }

    private AuthResult issueTokens(User user) {
        String access = jwt.generateAccessToken(user.getEmail(), user.getRole().getRoleName());
        String refresh = Tokens.newToken();
        refreshTokens.save(RefreshToken.builder()
                .user(user)
                .token(Tokens.sha256(refresh))   // only the hash is stored
                .expiryDate(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build());
        UserResponse ur = new UserResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getRole().getRoleName());
        return new AuthResult(AuthenticationResponse.of(access, ur), refresh);
    }
}
