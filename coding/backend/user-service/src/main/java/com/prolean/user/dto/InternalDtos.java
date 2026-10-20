package com.prolean.user.dto;

import com.prolean.user.domain.ProjectStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class InternalDtos {

    private InternalDtos() {
    }

    public record InternalUserResponse(
            UUID id,
            String username,
            String fullName,
            boolean enabled) {
    }

    public record AuthContextResponse(
            UUID userId,
            String username,
            String fullName,
            Set<String> roles,
            boolean enabled) {
    }

    public record BootstrapRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record BootstrapResponse(
            UUID userId,
            String username,
            Set<String> roles,
            boolean enabled,
            boolean created) {
    }

    public record AccessResponse(
            UUID projectId,
            UUID userId,
            boolean allowed,
            boolean member,
            boolean admin,
            ProjectStatus projectStatus,
            String projectCode) {
    }
}
