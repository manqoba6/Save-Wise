package com.savewise.dto;

/** What the service hands the controller: the JSON body, plus the refresh token to put in the cookie. */
public record AuthResult(AuthenticationResponse body, String refreshToken) {
}
