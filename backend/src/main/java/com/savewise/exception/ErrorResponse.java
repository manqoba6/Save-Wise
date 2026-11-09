package com.savewise.exception;

import java.time.Instant;

/** Matches the standard API response in SDD Chapter 14. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
