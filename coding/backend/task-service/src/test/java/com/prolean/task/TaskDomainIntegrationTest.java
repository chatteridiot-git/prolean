package com.prolean.task;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.task.domain.Task;
import com.prolean.task.domain.TaskStatus;
import com.prolean.task.dto.TaskDtos.CreateTaskRequest;
import com.prolean.task.dto.TaskDtos.UpdateTaskRequest;
import com.prolean.task.repository.TaskRepository;
import com.prolean.task.service.TaskService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class TaskDomainIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final WireMockServer USER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        USER.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("prolean.services.user.base-url", USER::baseUrl);
    }

    @BeforeAll
    static void start() {
        if (!USER.isRunning()) USER.start();
    }

    @AfterAll
    static void stop() {
        USER.stop();
    }

    @AfterEach
    void reset() {
        USER.resetAll();
    }

    @Autowired
    TaskService taskService;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    MockMvc mockMvc;

    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID picId = UUID.randomUUID();
    private final CurrentUser actor = new CurrentUser(actorId, "member.user", "Member User",
            Set.of("USER"));

    private void stubAccess(UUID project, UUID user, boolean allowed, boolean member, String status) {
        UUID resolvedProject = project == null ? projectId : project;
        UUID resolvedUser = user == null ? actorId : user;
        USER.stubFor(get(urlPathEqualTo("/internal/projects/" + resolvedProject + "/access"))
                .withQueryParam("userId", equalTo(resolvedUser.toString()))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"projectId":"%s","userId":"%s","allowed":%s,"member":%s,"admin":false,
                                 "projectStatus":"%s","projectCode":"INT-TASK"}
                                """.formatted(resolvedProject, resolvedUser, allowed, member, status))));
    }

    private void stubProfile(UUID user, boolean enabled) {
        USER.stubFor(get(urlPathEqualTo("/internal/users/" + user))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"%s","username":"pic.user","fullName":"PIC Person","enabled":%s}
                                """.formatted(user, enabled))));
    }

    private void stubActiveMembership() {
        stubAccess(projectId, actorId, true, true, "ACTIVE");
        stubAccess(projectId, picId, true, true, "ACTIVE");
        stubProfile(picId, true);
    }

    private CreateTaskRequest createRequest(UUID requestId, LocalDate start, LocalDate end,
            TaskStatus status, int percent) {
        return new CreateTaskRequest("Integration task", "Description", start, end, picId, status,
                percent, requestId);
    }

    @Test
    void createReplayPaginationAndDatabaseConstraints() {
        stubActiveMembership();
        UUID requestId = UUID.randomUUID();
        var created = taskService.create(projectId,
                createRequest(requestId, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 20), actor);

        assertThat(created.replay()).isFalse();
        assertThat(created.task().durationDays()).isEqualTo(5);
        assertThat(created.task().pic().name()).isEqualTo("PIC Person");
        assertThat(created.task().title()).isEqualTo("Integration task");

        var replay = taskService.create(projectId,
                createRequest(requestId, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 20), actor);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.task().id()).isEqualTo(created.task().id());

        UUID otherProject = UUID.randomUUID();
        stubAccess(otherProject, actorId, true, true, "ACTIVE");
        assertThatThrownBy(() -> taskService.create(otherProject,
                createRequest(requestId, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 20), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.DATA_CONFLICT));

        assertThatThrownBy(() -> taskService.create(projectId,
                createRequest(UUID.randomUUID(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                        TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
        assertThatThrownBy(() -> taskService.create(projectId,
                createRequest(UUID.randomUUID(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                        TaskStatus.CLOSED, 99), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_COMPLETION_FOR_CLOSED));

        var page = taskService.list(projectId, PageQuery.of(0, 10, "createdAt,desc"),
                TaskStatus.OPEN, picId, "integration", actor);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);

        Task stored = taskRepository.findById(created.task().id()).orElseThrow();
        assertThat(stored.getDurationDays()).isEqualTo(5);
        assertThat(stored.getPicName()).isEqualTo("PIC Person");
    }

    @Test
    void transitionsProgressDeleteAndArchivedAccess() {
        stubActiveMembership();
        var created = taskService.create(projectId,
                createRequest(UUID.randomUUID(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 10), actor);
        UUID taskId = created.task().id();

        assertThat(taskService.changeStatus(projectId, taskId, TaskStatus.IN_PROGRESS, actor)
                .status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(taskService.changeProgress(projectId, taskId, 55, actor).percentComplete())
                .isEqualTo(55);
        assertThat(taskService.changeStatus(projectId, taskId, TaskStatus.CLOSED, actor)
                .percentComplete()).isEqualTo(100);
        assertThatThrownBy(() -> taskService.changeProgress(projectId, taskId, 20, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TASK_CLOSED));
        assertThat(taskService.changeStatus(projectId, taskId, TaskStatus.IN_PROGRESS, actor)
                .percentComplete()).isEqualTo(100);
        assertThatThrownBy(() -> taskService.changeStatus(projectId, taskId, TaskStatus.OPEN, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));

        var updated = taskService.update(projectId, taskId,
                new UpdateTaskRequest("Updated title", "Updated", LocalDate.of(2026, 1, 6),
                        LocalDate.of(2026, 1, 10), picId, TaskStatus.IN_PROGRESS, 30), actor);
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.durationDays()).isEqualTo(5);

        assertThatThrownBy(() -> taskService.update(projectId, taskId,
                new UpdateTaskRequest("Invalid", null, LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 6), picId, TaskStatus.OPEN, 20), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));

        stubAccess(projectId, actorId, true, true, "ARCHIVED");
        assertThatThrownBy(() -> taskService.changeProgress(projectId, taskId, 20, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED));

        stubAccess(projectId, actorId, true, true, "ACTIVE");
        taskService.delete(projectId, taskId, actor);
        assertThat(taskRepository.findById(taskId)).isEmpty();
        assertThatThrownBy(() -> taskService.get(projectId, taskId, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    @Test
    void forbiddenPicAndMissingTaskErrors() {
        stubAccess(projectId, actorId, false, false, "ACTIVE");
        assertThatThrownBy(() -> taskService.list(projectId, PageQuery.of(0, 20, null), null, null,
                null, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));

        stubActiveMembership();
        assertThatThrownBy(() -> taskService.get(projectId, UUID.randomUUID(), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TASK_NOT_FOUND));

        stubAccess(projectId, picId, true, false, "ACTIVE");
        assertThatThrownBy(() -> taskService.create(projectId,
                createRequest(UUID.randomUUID(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PIC_NOT_MEMBER));

        stubAccess(projectId, picId, true, true, "ACTIVE");
        stubProfile(picId, false);
        assertThatThrownBy(() -> taskService.create(projectId,
                createRequest(UUID.randomUUID(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9),
                        TaskStatus.OPEN, 0), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PIC_NOT_MEMBER));

        assertThatThrownBy(() -> taskService.create(projectId,
                new CreateTaskRequest("   ", null, LocalDate.of(2026, 1, 5),
                        LocalDate.of(2026, 1, 9), picId, TaskStatus.OPEN, 0, UUID.randomUUID()),
                actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void httpContractsReturnProblemDetailsAndPaginatedLists() throws Exception {
        stubActiveMembership();
        var auth = jwt().jwt(jwt -> jwt
                .subject(actorId.toString())
                .claim("username", actor.username())
                .claim("fullName", actor.fullName())
                .claim("roles", java.util.List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + projectId + "/tasks")
                        .param("page", "0")
                        .param("size", "10")
                        .with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + projectId + "/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects/" + projectId + "/tasks")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","startDate":null,"endDate":null,"picUserId":null,
                                 "status":null,"percentComplete":null,"requestId":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + projectId + "/tasks/" + UUID.randomUUID())
                        .with(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }
}
