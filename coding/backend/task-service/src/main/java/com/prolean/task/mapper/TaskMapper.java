package com.prolean.task.mapper;

import com.prolean.task.domain.Task;
import com.prolean.task.dto.TaskDtos.Pic;
import com.prolean.task.dto.TaskDtos.TaskResponse;
import com.prolean.task.dto.TaskDtos.TaskSummary;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getDescription(),
                task.getStartDate(),
                task.getEndDate(),
                duration(task),
                new Pic(task.getPicUserId(), task.getPicName()),
                task.getStatus(),
                task.getPercentComplete(),
                task.getCreatedBy(),
                task.getUpdatedBy(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    public static TaskSummary toSummary(Task task) {
        return new TaskSummary(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getStartDate(),
                task.getEndDate(),
                duration(task),
                new Pic(task.getPicUserId(), task.getPicName()),
                task.getStatus(),
                task.getPercentComplete());
    }

    private static int duration(Task task) {
        return task.calculatedDurationDays();
    }
}
