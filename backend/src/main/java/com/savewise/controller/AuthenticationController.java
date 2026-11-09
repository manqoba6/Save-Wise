package com.savewise.controller;

import com.savewise.dto.*;
import com.savewise.exception.ApiException;
import com.savewise.exception.ErrorResponse;
import com.savewise.security.RefreshCookie;
import com.savewise.service.AuthenticationService;
import com.savewise.service.CurrentUserService;
import com.savewise.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService auth;
    private final PasswordService passwords;
    private final CurrentUserService currentUser;
    private final RefreshCookie cookie;

    public AuthenticationController(AuthenticationService auth, PasswordService passwords,
                                    CurrentUserService currentUser, RefreshCookie cookie) {
        this.auth = auth;
        this.passwords = passwords;
        this.currentUser = currentUser;
        this.cookie = cookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withCookie(HttpStatus.CREATED, auth.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(HttpStatus.OK, auth.login(req));
    }

    /** Needs no body: the browser sends the HttpOnly cookie by itself. A bad or missing cookie is cleared. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
                                     HttpServletRequest request) {
        try {
            return withCookie(HttpStatus.OK, auth.refresh(refreshToken));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
                    .body(new ErrorResponse(Instant.now(), e.getStatus().value(), e.getStatus().getReasonPhrase(),
                            e.getMessage(), request.getRequestURI()));
        }
    }

    /** Works even when the access token has expired, so a user can always sign out. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear().toString()).build();
    }

    /** Same response whether or not the email has an account. */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwords.forgotPassword(req.email());
        return new MessageResponse("If an account exists for that email, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwords.resetPassword(req);
        return new MessageResponse("Your password has been reset. Please log in with your new password.");
    }

    /** Requires a logged-in user (Bearer token). */
    @PostMapping("/change-password")
    public MessageResponse changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest req) {
        passwords.changePassword(currentUser.get(authentication), req);
        return new MessageResponse("Password changed. Other devices will need to log in again.");
    }

    private ResponseEntity<AuthenticationResponse> withCookie(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.issue(result.refreshToken()).toString())
                .body(result.body());
    }
}
