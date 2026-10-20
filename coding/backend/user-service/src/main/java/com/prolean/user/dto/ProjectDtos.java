package com.prolean.user.dto;

import com.prolean.user.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{2,30}") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull UUID requestId,
            @Size(max = 200) List<@NotNull UUID> memberIds) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record ProjectStatusRequest(@NotNull ProjectStatus status) {
    }

    public record AddMembersRequest(
            @NotEmpty @Size(max = 200) List<@NotNull UUID> userIds) {
    }

    public record ProjectSummary(
            UUID id,
            String code,
            String name,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate endDate,
            long memberCount) {
    }

    public record ProjectResponse(
            UUID id,
            String code,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status,
            long memberCount,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MemberResponse(
            UUID userId,
            String username,
            String fullName,
            String email,
            Instant addedAt) {
    }

    public record MembersAddedResponse(int added, int alreadyMembers) {
    }
}
