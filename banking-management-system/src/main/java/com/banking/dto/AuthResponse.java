package com.banking.dto;

public record AuthResponse(String token, String tokenType, String role, long expiresInMs) {
}
