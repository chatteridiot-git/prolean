package com.prolean.task.api;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.security.CurrentUser;
import com.prolean.common.web.RequestHeaders;
import com.prolean.task.domain.TaskStatus;
import com.prolean.task.dto.TaskDtos.CreateTaskRequest;
import com.prolean.task.dto.TaskDtos.ProgressRequest;
import com.prolean.task.dto.TaskDtos.StatusRequest;
import com.prolean.task.dto.TaskDtos.TaskResponse;
import com.prolean.task.dto.TaskDtos.TaskSummary;
import com.prolean.task.dto.TaskDtos.UpdateTaskRequest;
import com.prolean.task.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public PageResponse<TaskSummary> list(@PathVariable UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID picUserId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return taskService.list(projectId, PageQuery.of(page, size, sort), status, picUserId, q,
                CurrentUser.from(jwt));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request, @AuthenticationPrincipal Jwt jwt) {
        TaskService.TaskResult result = taskService.create(projectId, request, CurrentUser.from(jwt));
        var builder = ResponseEntity.status(result.replay() ? 200 : 201);
        if (result.replay()) {
            builder.header(RequestHeaders.IDEMPOTENT_REPLAY, "true");
        } else {
            builder.location(URI.create("/api/v1/projects/" + projectId + "/tasks/" + result.task().id()));
        }
        return builder.body(result.task());
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt) {
        return taskService.get(projectId, taskId, CurrentUser.from(jwt));
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request, @AuthenticationPrincipal Jwt jwt) {
        return taskService.update(projectId, taskId, request, CurrentUser.from(jwt));
    }

    @PatchMapping("/{taskId}/status")
    public TaskResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return taskService.changeStatus(projectId, taskId, request.status(), CurrentUser.from(jwt));
    }

    @PatchMapping("/{taskId}/progress")
    public TaskResponse changeProgress(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @Valid @RequestBody ProgressRequest request, @AuthenticationPrincipal Jwt jwt) {
        return taskService.changeProgress(projectId, taskId, request.percentComplete(),
                CurrentUser.from(jwt));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @AuthenticationPrincipal Jwt jwt) {
        taskService.delete(projectId, taskId, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }
}
