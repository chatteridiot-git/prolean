package com.prolean.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 200) String password) {
    }

    public record UserSummary(UUID id, String username, String fullName, Set<String> roles) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
    }

    public record RefreshResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record MeResponse(UUID id, String username, String fullName, Set<String> roles) {
    }

    public record PasswordSetRequest(
            @NotBlank @Size(min = 8, max = 64) String newPassword) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword) {
    }

    public record RevokeRequest(String reason) {
    }
}
