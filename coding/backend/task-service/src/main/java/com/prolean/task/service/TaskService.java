package com.prolean.task.service;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.task.client.ProjectAccessClient;
import com.prolean.task.domain.Task;
import com.prolean.task.domain.TaskStatus;
import com.prolean.task.dto.TaskDtos.CreateTaskRequest;
import com.prolean.task.dto.TaskDtos.TaskResponse;
import com.prolean.task.dto.TaskDtos.TaskSummary;
import com.prolean.task.dto.TaskDtos.UpdateTaskRequest;
import com.prolean.task.mapper.TaskMapper;
import com.prolean.task.repository.TaskRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final Set<String> SORT_FIELDS = Set.of(
            "title", "startDate", "endDate", "durationDays", "status", "percentComplete",
            "createdAt", "updatedAt");
    private static final int MAX_RANGE_DAYS = 3650;

    private final TaskRepository taskRepository;
    private final ProjectAccessClient accessClient;

    public TaskService(TaskRepository taskRepository, ProjectAccessClient accessClient) {
        this.taskRepository = taskRepository;
        this.accessClient = accessClient;
    }

    public PageResponse<TaskSummary> list(UUID projectId, PageQuery query, TaskStatus status,
            UUID picUserId, String q, CurrentUser actor) {
        requireAccess(projectId, actor);
        Pageable pageable = query.toPageable(SORT_FIELDS, "createdAt,desc");
        Page<Task> page = taskRepository.search(projectId, status, picUserId,
                q == null || q.isBlank() ? null : q.trim(), pageable);
        return PageResponse.of(page.map(TaskMapper::toSummary).getContent(), page);
    }

    public TaskResponse get(UUID projectId, UUID taskId, CurrentUser actor) {
        requireAccess(projectId, actor);
        return TaskMapper.toResponse(requireTask(projectId, taskId));
    }

    @Transactional
    public TaskResult create(UUID projectId, CreateTaskRequest request, CurrentUser actor) {
        ProjectAccessClient.Access access = requireAccess(projectId, actor);
        requireActive(access);
        var existing = taskRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            Task task = existing.get();
            if (!task.getProjectId().equals(projectId)) {
                throw new ApiException(ErrorCode.DATA_CONFLICT,
                        "The request id belongs to a task in another project.");
            }
            return new TaskResult(TaskMapper.toResponse(task), true);
        }

        validateDates(request.startDate(), request.endDate());
        String title = requireTitle(request.title());
        TaskStatus status = request.status() == null ? TaskStatus.OPEN : request.status();
        int percent = request.percentComplete() == null ? 0 : request.percentComplete();
        if (status == TaskStatus.CLOSED && percent < 100) {
            throw new ApiException(ErrorCode.INVALID_COMPLETION_FOR_CLOSED);
        }
        ProjectAccessClient.Profile pic = validatePic(projectId, request.picUserId());

        Task task = new Task();
        task.setProjectId(projectId);
        task.setRequestId(request.requestId());
        task.setTitle(title);
        task.setDescription(request.description());
        task.setStartDate(request.startDate());
        task.setEndDate(request.endDate());
        task.setPicUserId(pic.id());
        task.setPicName(pic.fullName());
        task.setStatus(status);
        task.setPercentComplete(status == TaskStatus.CLOSED ? 100 : percent);
        task.setCreatedBy(actor.id());
        task.setUpdatedBy(actor.id());
        Task saved = taskRepository.save(task);
        requireAccess(projectId, actor);
        return new TaskResult(TaskMapper.toResponse(saved), false);
    }

    @Transactional
    public TaskResponse update(UUID projectId, UUID taskId, UpdateTaskRequest request, CurrentUser actor) {
        ProjectAccessClient.Access access = requireAccess(projectId, actor);
        requireActive(access);
        Task task = requireTask(projectId, taskId);
        validateDates(request.startDate(), request.endDate());
        String title = requireTitle(request.title());
        int percent = request.percentComplete();
        TaskStatus requested = request.status();
        if (requested == TaskStatus.CLOSED && percent < 100) {
            throw new ApiException(ErrorCode.INVALID_COMPLETION_FOR_CLOSED);
        }
        validateTransition(task.getStatus(), requested);
        ProjectAccessClient.Profile pic = validatePic(projectId, request.picUserId());

        task.setTitle(title);
        task.setDescription(request.description());
        task.setStartDate(request.startDate());
        task.setEndDate(request.endDate());
        task.setPicUserId(pic.id());
        task.setPicName(pic.fullName());
        task.setStatus(requested);
        task.setPercentComplete(requested == TaskStatus.CLOSED ? 100 : percent);
        task.setUpdatedBy(actor.id());
        Task saved = taskRepository.save(task);
        requireAccess(projectId, actor);
        return TaskMapper.toResponse(saved);
    }

    @Transactional
    public TaskResponse changeStatus(UUID projectId, UUID taskId, TaskStatus target, CurrentUser actor) {
        ProjectAccessClient.Access access = requireAccess(projectId, actor);
        requireActive(access);
        Task task = requireTask(projectId, taskId);
        validateTransition(task.getStatus(), target);
        task.setStatus(target);
        if (target == TaskStatus.CLOSED) {
            task.setPercentComplete(100);
        }
        task.setUpdatedBy(actor.id());
        Task saved = taskRepository.save(task);
        requireAccess(projectId, actor);
        return TaskMapper.toResponse(saved);
    }

    @Transactional
    public TaskResponse changeProgress(UUID projectId, UUID taskId, int percent, CurrentUser actor) {
        ProjectAccessClient.Access access = requireAccess(projectId, actor);
        requireActive(access);
        Task task = requireTask(projectId, taskId);
        if (task.getStatus() == TaskStatus.CLOSED) {
            throw new ApiException(ErrorCode.TASK_CLOSED);
        }
        task.setPercentComplete(percent);
        task.setUpdatedBy(actor.id());
        Task saved = taskRepository.save(task);
        requireAccess(projectId, actor);
        return TaskMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID projectId, UUID taskId, CurrentUser actor) {
        ProjectAccessClient.Access access = requireAccess(projectId, actor);
        requireActive(access);
        Task task = requireTask(projectId, taskId);
        requireAccess(projectId, actor);
        taskRepository.delete(task);
    }

    private ProjectAccessClient.Access requireAccess(UUID projectId, CurrentUser actor) {
        ProjectAccessClient.Access access = accessClient.access(projectId, actor.id());
        if (access == null || !access.allowed()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not a member of this project.");
        }
        return access;
    }

    private void requireActive(ProjectAccessClient.Access access) {
        if (!"ACTIVE".equals(access.projectStatus())) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED);
        }
    }

    private ProjectAccessClient.Profile validatePic(UUID projectId, UUID picUserId) {
        ProjectAccessClient.Access picAccess = accessClient.access(projectId, picUserId);
        if (picAccess == null || !picAccess.member()) {
            throw new ApiException(ErrorCode.PIC_NOT_MEMBER);
        }
        ProjectAccessClient.Profile profile = accessClient.profile(picUserId);
        if (profile == null || !profile.enabled()) {
            throw new ApiException(ErrorCode.PIC_NOT_MEMBER);
        }
        return profile;
    }

    private Task requireTask(UUID projectId, UUID taskId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE,
                    "Task range must not exceed 10 years (3650 days).");
        }
    }

    private String requireTitle(String title) {
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.validation("title", "must not be blank");
        }
        return trimmed;
    }

    private void validateTransition(TaskStatus current, TaskStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case OPEN -> target == TaskStatus.IN_PROGRESS || target == TaskStatus.CLOSED;
            case IN_PROGRESS -> target == TaskStatus.CLOSED;
            case CLOSED -> target == TaskStatus.IN_PROGRESS;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Transition " + current + " -> " + target + " is not allowed.");
        }
    }

    public record TaskResult(TaskResponse task, boolean replay) {
    }
}
