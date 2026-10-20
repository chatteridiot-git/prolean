package com.prolean.task.dto;

import com.prolean.task.domain.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull UUID picUserId,
            TaskStatus status,
            @Min(0) @Max(100) Integer percentComplete,
            @NotNull UUID requestId) {
    }

    public record UpdateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull UUID picUserId,
            @NotNull TaskStatus status,
            @NotNull @Min(0) @Max(100) Integer percentComplete) {
    }

    public record StatusRequest(@NotNull TaskStatus status) {
    }

    public record ProgressRequest(@NotNull @Min(0) @Max(100) Integer percentComplete) {
    }

    public record Pic(UUID userId, String name) {
    }

    public record TaskResponse(
            UUID id,
            UUID projectId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            int durationDays,
            Pic pic,
            TaskStatus status,
            int percentComplete,
            UUID createdBy,
            UUID updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TaskSummary(
            UUID id,
            UUID projectId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int durationDays,
            Pic pic,
            TaskStatus status,
            int percentComplete) {
    }
}
