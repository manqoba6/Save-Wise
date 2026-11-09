package com.savewise.dto;

/** The refresh token is deliberately NOT here: it travels only in an HttpOnly cookie that JavaScript cannot read. */
public record AuthenticationResponse(String accessToken, String tokenType, UserResponse user) {
    public static AuthenticationResponse of(String access, UserResponse user) {
        return new AuthenticationResponse(access, "Bearer", user);
    }
}
