package com.savewise.service;

import com.savewise.dto.ChangePasswordRequest;
import com.savewise.dto.ResetPasswordRequest;
import com.savewise.entity.PasswordResetToken;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.repository.PasswordResetTokenRepository;
import com.savewise.repository.RefreshTokenRepository;
import com.savewise.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final String frontendUrl;
    private final long resetMinutes;

    public PasswordService(UserRepository users, PasswordResetTokenRepository resetTokens,
                           RefreshTokenRepository refreshTokens, PasswordEncoder encoder, EmailService email,
                           @Value("${app.frontend-url}") String frontendUrl,
                           @Value("${app.password-reset-minutes}") long resetMinutes) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.email = email;
        this.frontendUrl = frontendUrl;
        this.resetMinutes = resetMinutes;
    }

    /**
     * Always completes silently, whether or not the email exists, so the endpoint
     * cannot be used to discover which emails have accounts.
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(rawEmail.trim().toLowerCase()).filter(User::isEnabled).ifPresent(user -> {
            resetTokens.deleteByUserId(user.getId()); // only the newest link works
            String token = newToken();
            resetTokens.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash(token))
                    .expiryDate(LocalDateTime.now().plusMinutes(resetMinutes))
                    .build());
            String link = frontendUrl + "/reset-password?token=" + token;
            email.send(user.getEmail(), "Reset your SaveWise password",
                    "Hi " + user.getFirstName() + ",\n\nUse this link to choose a new password. It expires in "
                            + resetMinutes + " minutes:\n" + link
                            + "\n\nIf you did not ask for this, you can ignore this email.");
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken t = resetTokens.findByTokenHash(hash(req.token()))
                .filter(x -> !x.isUsed() && x.getExpiryDate().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired."));
        User user = t.getUser();
        user.setPassword(encoder.encode(req.newPassword()));
        t.setUsed(true);
        refreshTokens.deleteByUserId(user.getId()); // sign out everywhere
    }

    @Transactional
    public void changePassword(User current, ChangePasswordRequest req) {
        User user = users.findById(current.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists."));
        if (!encoder.matches(req.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (encoder.matches(req.newPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from the current one.");
        }
        user.setPassword(encoder.encode(req.newPassword()));
        refreshTokens.deleteByUserId(user.getId()); // other sessions must log in again
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
