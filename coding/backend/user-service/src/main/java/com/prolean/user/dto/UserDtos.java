package com.prolean.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9._-]{3,50}") String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String fullName,
            @Size(max = 2) Set<@Pattern(regexp = "ADMIN|USER") String> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record StatusRequest(@NotNull Boolean enabled) {
    }

    public record RolesRequest(
            @NotEmpty @Size(max = 2) Set<@Pattern(regexp = "ADMIN|USER") String> roles) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String fullName,
            boolean enabled,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RoleResponse(UUID id, String name, String description) {
    }
}
