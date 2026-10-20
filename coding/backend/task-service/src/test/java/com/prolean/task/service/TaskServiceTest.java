package com.prolean.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.task.client.ProjectAccessClient;
import com.prolean.task.domain.Task;
import com.prolean.task.domain.TaskStatus;
import com.prolean.task.dto.TaskDtos.CreateTaskRequest;
import com.prolean.task.dto.TaskDtos.ProgressRequest;
import com.prolean.task.dto.TaskDtos.StatusRequest;
import com.prolean.task.dto.TaskDtos.TaskResponse;
import com.prolean.task.dto.TaskDtos.UpdateTaskRequest;
import com.prolean.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository tasks;
    @Mock
    private ProjectAccessClient accessClient;

    private TaskService service;
    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID picId = UUID.randomUUID();
    private final CurrentUser actor = new CurrentUser(actorId, "member", "Member", Set.of("USER"));
    private final ProjectAccessClient.Access allowedAccess =
            new ProjectAccessClient.Access(projectId, actorId, true, true, false, "ACTIVE", "ALPHA");
    private final ProjectAccessClient.Profile picProfile =
            new ProjectAccessClient.Profile(picId, "pic", "PIC Name", true);

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, accessClient);
    }

    private void stubAccess() {
        when(accessClient.access(any(), any())).thenReturn(allowedAccess);
    }

    private void stubPic() {
        when(accessClient.profile(picId)).thenReturn(picProfile);
    }

    private static Task task(TaskStatus status, int percent) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setProjectId(UUID.randomUUID());
        task.setRequestId(UUID.randomUUID());
        task.setTitle("Task");
        task.setStartDate(LocalDate.of(2026, 11, 2));
        task.setEndDate(LocalDate.of(2026, 11, 6));
        task.setPicUserId(UUID.randomUUID());
        task.setPicName("PIC");
        task.setStatus(status);
        task.setPercentComplete(percent);
        task.setCreatedBy(UUID.randomUUID());
        task.setUpdatedBy(UUID.randomUUID());
        return task;
    }

    private CreateTaskRequest createRequest(TaskStatus status, Integer percent) {
        return new CreateTaskRequest(" Task ", "Description",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), picId,
                status, percent, UUID.randomUUID());
    }

    @Test
    void createPersistsWithSnapshotPicAndComputedDuration() {
        stubAccess();
        stubPic();
        when(tasks.findByRequestId(any())).thenReturn(Optional.empty());
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateTaskRequest request = createRequest(TaskStatus.OPEN, 25);
        TaskService.TaskResult result = service.create(projectId, request, actor);

        assertThat(result.replay()).isFalse();
        assertThat(result.task().durationDays()).isEqualTo(5);
        assertThat(result.task().title()).isEqualTo("Task");
        assertThat(result.task().pic().name()).isEqualTo("PIC Name");
        assertThat(result.task().percentComplete()).isEqualTo(25);
    }

    @Test
    void createForcesClosedToHundred() {
        stubAccess();
        stubPic();
        when(tasks.findByRequestId(any())).thenReturn(Optional.empty());
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TaskService.TaskResult result = service.create(projectId,
                createRequest(TaskStatus.CLOSED, 100), actor);

        assertThat(result.task().status()).isEqualTo(TaskStatus.CLOSED);
        assertThat(result.task().percentComplete()).isEqualTo(100);
    }

    @Test
    void createRejectsClosedBelowHundred() {
        stubAccess();
        CreateTaskRequest request = createRequest(TaskStatus.CLOSED, 99);
        assertThatThrownBy(() -> service.create(projectId, request, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_COMPLETION_FOR_CLOSED));
        verify(tasks, never()).save(any());
    }

    @Test
    void createReplaysSameProjectRequest() {
        Task existing = task(TaskStatus.OPEN, 10);
        existing.setProjectId(projectId);
        stubAccess();
        when(tasks.findByRequestId(existing.getRequestId())).thenReturn(Optional.of(existing));

        TaskService.TaskResult result = service.create(projectId,
                new CreateTaskRequest("ignored", null, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2), picId, null, null, existing.getRequestId()), actor);

        assertThat(result.replay()).isTrue();
        assertThat(result.task().id()).isEqualTo(existing.getId());
        verify(tasks, never()).save(any());
    }

    @Test
    void createRejectsRequestIdFromAnotherProject() {
        Task existing = task(TaskStatus.OPEN, 10);
        existing.setProjectId(UUID.randomUUID());
        stubAccess();
        when(tasks.findByRequestId(existing.getRequestId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(projectId,
                new CreateTaskRequest("ignored", null, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2), picId, null, null, existing.getRequestId()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DATA_CONFLICT));
    }

    @Test
    void createValidatesAccessProjectStateDatesAndPic() {
        when(accessClient.access(any(), any())).thenReturn(
                new ProjectAccessClient.Access(projectId, actorId, false, false, false, "ACTIVE", "ALPHA"));
        assertThatThrownBy(() -> service.create(projectId, createRequest(TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        when(accessClient.access(any(), any())).thenReturn(
                new ProjectAccessClient.Access(projectId, actorId, true, true, false, "ARCHIVED", "ALPHA"));
        assertThatThrownBy(() -> service.create(projectId, createRequest(TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED));

        when(accessClient.access(any(), any())).thenReturn(allowedAccess);
        when(tasks.findByRequestId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTaskRequest("Task", null, LocalDate.of(2026, 11, 2),
                        LocalDate.of(2026, 11, 1), picId, null, null, UUID.randomUUID()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTaskRequest("Task", null, LocalDate.of(2020, 1, 1),
                        LocalDate.of(2035, 1, 1), picId, null, null, UUID.randomUUID()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTaskRequest(" ", null, LocalDate.of(2026, 11, 2),
                        LocalDate.of(2026, 11, 3), picId, null, null, UUID.randomUUID()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void createRejectsPicWhoIsNotAnActiveMember() {
        when(tasks.findByRequestId(any())).thenReturn(Optional.empty());
        when(accessClient.access(any(), any()))
                .thenReturn(new ProjectAccessClient.Access(projectId, picId, true, false, true, "ACTIVE", "ALPHA"));
        assertThatThrownBy(() -> service.create(projectId, createRequest(TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PIC_NOT_MEMBER));

        when(accessClient.access(any(), any())).thenReturn(
                new ProjectAccessClient.Access(projectId, picId, true, true, false, "ACTIVE", "ALPHA"));
        when(accessClient.profile(picId))
                .thenReturn(new ProjectAccessClient.Profile(picId, "pic", "PIC", false));
        assertThatThrownBy(() -> service.create(projectId, createRequest(TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PIC_NOT_MEMBER));
    }

    @Test
    void listAndGetCheckAccessAndProjectScopedTask() {
        stubAccess();
        Task task = task(TaskStatus.OPEN, 20);
        when(tasks.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));

        assertThat(service.list(projectId, PageQuery.of(0, 20, null), null, null, " ", actor)
                .content()).hasSize(1);
        assertThat(service.get(projectId, task.getId(), actor).id()).isEqualTo(task.getId());

        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(projectId, task.getId(), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    void updateAppliesFullReplacementAndChecksTransition() {
        stubAccess();
        stubPic();
        Task task = task(TaskStatus.OPEN, 0);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        TaskResponse response = service.update(projectId, task.getId(),
                new UpdateTaskRequest(" New title ", "New", LocalDate.of(2026, 11, 9),
                        LocalDate.of(2026, 11, 13), picId, TaskStatus.IN_PROGRESS, 40), actor);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.percentComplete()).isEqualTo(40);
    }

    @Test
    void updateRejectsClosedBelowHundredAndInvalidTransition() {
        stubAccess();
        Task task = task(TaskStatus.OPEN, 0);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(projectId, task.getId(),
                new UpdateTaskRequest("Task", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                        picId, TaskStatus.CLOSED, 50), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_COMPLETION_FOR_CLOSED));

        task.setStatus(TaskStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.update(projectId, task.getId(),
                new UpdateTaskRequest("Task", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                        picId, TaskStatus.OPEN, 50), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void updateAllowsReopenAndClosedUpdate() {
        stubAccess();
        stubPic();
        Task task = task(TaskStatus.CLOSED, 100);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        TaskResponse reopened = service.update(projectId, task.getId(),
                new UpdateTaskRequest("Task", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                        picId, TaskStatus.IN_PROGRESS, 40), actor);
        assertThat(reopened.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        TaskResponse closed = service.update(projectId, task.getId(),
                new UpdateTaskRequest("Task", null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6),
                        picId, TaskStatus.CLOSED, 100), actor);
        assertThat(closed.percentComplete()).isEqualTo(100);
    }

    @Test
    void statusEndpointValidatesTransitionsAndClosesAtHundred() {
        stubAccess();
        Task task = task(TaskStatus.OPEN, 30);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        TaskResponse inProgress = service.changeStatus(projectId, task.getId(),
                TaskStatus.IN_PROGRESS, actor);
        assertThat(inProgress.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        TaskResponse closed = service.changeStatus(projectId, task.getId(), TaskStatus.CLOSED, actor);
        assertThat(closed.percentComplete()).isEqualTo(100);

        assertThatThrownBy(() -> service.changeStatus(projectId, task.getId(), TaskStatus.OPEN, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void progressEndpointRejectsClosedAndUpdatesOpenTask() {
        stubAccess();
        Task task = task(TaskStatus.OPEN, 10);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        assertThat(service.changeProgress(projectId, task.getId(), 80, actor).percentComplete())
                .isEqualTo(80);

        task.setStatus(TaskStatus.CLOSED);
        task.setPercentComplete(100);
        assertThatThrownBy(() -> service.changeProgress(projectId, task.getId(), 50, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TASK_CLOSED));
    }

    @Test
    void deleteRemovesScopedTask() {
        stubAccess();
        Task task = task(TaskStatus.OPEN, 0);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));

        service.delete(projectId, task.getId(), actor);

        verify(tasks).delete(task);
    }

    @Test
    void archivedAndMissingProjectAccessAreRejectedForWrites() {
        when(accessClient.access(any(), any())).thenReturn(
                new ProjectAccessClient.Access(projectId, actorId, true, true, false, "ARCHIVED", "ALPHA"));
        Task task = task(TaskStatus.OPEN, 0);
        assertThatThrownBy(() -> service.delete(projectId, task.getId(), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED));
    }

    @Test
    void requestProgressRecordIsUsable() {
        assertThat(new ProgressRequest(50).percentComplete()).isEqualTo(50);
        assertThat(new StatusRequest(TaskStatus.CLOSED).status()).isEqualTo(TaskStatus.CLOSED);
    }
}
