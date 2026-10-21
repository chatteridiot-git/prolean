package com.prolean.user;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.user.domain.AppUser;
import com.prolean.user.domain.ProjectStatus;
import com.prolean.user.dto.InternalDtos.AuthContextResponse;
import com.prolean.user.dto.ProjectDtos.AddMembersRequest;
import com.prolean.user.dto.ProjectDtos.CreateProjectRequest;
import com.prolean.user.dto.ProjectDtos.UpdateProjectRequest;
import com.prolean.user.dto.UserDtos.CreateUserRequest;
import com.prolean.user.dto.UserDtos.RolesRequest;
import com.prolean.user.dto.UserDtos.UpdateUserRequest;
import com.prolean.user.repository.UserRepository;
import com.prolean.user.service.InternalUserService;
import com.prolean.user.service.ProjectService;
import com.prolean.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class UserDomainIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AUTH.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("prolean.services.auth.base-url", AUTH::baseUrl);
    }

    @BeforeAll
    static void start() {
        if (!AUTH.isRunning()) AUTH.start();
    }

    @AfterAll
    static void stop() {
        AUTH.stop();
    }

    @BeforeEach
    void stub() {
        AUTH.resetAll();
        AUTH.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathMatching("/internal/users/.*/revoke-tokens"))
                .willReturn(aResponse().withStatus(204)));
    }

    @AfterEach
    void reset() {
        AUTH.resetAll();
    }

    @Autowired
    UserService userService;
    @Autowired
    ProjectService projectService;
    @Autowired
    InternalUserService internalUserService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MockMvc mockMvc;

    private CurrentUser actor(AppUser user, String... roles) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getFullName(), Set.of(roles));
    }

    private AppUser createUser(String username, String role) {
        AppUser admin = new AppUser();
        admin.setUsername("seed." + username);
        admin.setEmail("seed." + username + "@example.com");
        admin.setFullName("Seed " + username);
        admin.setEnabled(true);
        admin.setCreatedBy(UUID.randomUUID());
        admin.setUpdatedBy(UUID.randomUUID());
        AppUser saved = userRepository.saveAndFlush(admin);
        CurrentUser seedActor = new CurrentUser(saved.getId(), saved.getUsername(), saved.getFullName(),
                Set.of("ADMIN"));
        return userRepository.findByUsername(
                userService.create(new CreateUserRequest(username, username + "@example.com",
                        "User " + username, Set.of(role)), seedActor).username()).orElseThrow();
    }

    @Test
    void userUniquenessStatusAndLastAdminGuard() {
        AppUser admin = createUser("integration.admin", "ADMIN");
        CurrentUser adminActor = actor(admin, "ADMIN");

        AppUser userA = createUser("integration.alice", "USER");
        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("integration.alice", "other@example.com", "Duplicate", Set.of("USER")),
                adminActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.USERNAME_TAKEN));
        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("integration.alice2", "integration.alice@example.com", "Duplicate",
                        Set.of("USER")),
                adminActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));

        AppUser userB = createUser("integration.bob", "USER");
        assertThatThrownBy(() -> userService.update(userB.getId(),
                new UpdateUserRequest("integration.alice@example.com", "Bob"), adminActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));

        var page = userService.list(PageQuery.of(0, 20, "username,asc"), "integration.", null, "USER");
        assertThat(page.content()).extracting("username")
                .contains("integration.alice", "integration.bob");

        var roles = userService.replaceRoles(userA.getId(),
                new RolesRequest(Set.of("ADMIN", "USER")), adminActor);
        assertThat(roles.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        var demoted = userService.replaceRoles(userA.getId(), new RolesRequest(Set.of("USER")), adminActor);
        assertThat(demoted.roles()).containsExactly("USER");
        AUTH.verify(postRequestedFor(urlPathMatching("/internal/users/.*/revoke-tokens")));

        var disabled = userService.changeStatus(userB.getId(), false, adminActor);
        assertThat(disabled.enabled()).isFalse();
        AuthContextResponse context = internalUserService.authContext(userB.getId());
        assertThat(context.enabled()).isFalse();

        // Keep only the target admin active so the last-admin guard is deterministic.
        List<AppUser> admins = userRepository.search(null, true, "ADMIN", Pageable.unpaged())
                .getContent();
        for (AppUser other : admins) {
            if (!other.getId().equals(admin.getId())) {
                other.setEnabled(false);
                userRepository.saveAndFlush(other);
            }
        }
        assertThatThrownBy(() -> userService.changeStatus(admin.getId(), false, actor(userA, "USER")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.LAST_ADMIN));
        assertThatThrownBy(() -> userService.changeStatus(admin.getId(), false, adminActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.SELF_DEACTIVATION));

        assertThatThrownBy(() -> userService.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void projectMembershipSearchAndArchiving() {
        AppUser owner = createUser("project.owner", "USER");
        AppUser member = createUser("project.member", "USER");
        CurrentUser ownerActor = actor(owner, "USER");

        UUID requestId = UUID.randomUUID();
        var created = projectService.create(new CreateProjectRequest("PRJ-INT", "Integration project",
                "Created by integration test", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30),
                requestId, List.of(owner.getId(), member.getId())), ownerActor);
        assertThat(created.replay()).isFalse();
        assertThat(created.project().memberCount()).isEqualTo(2);

        // A non-admin sees only projects where they are a member.
        var ownerProjects = projectService.list(PageQuery.of(0, 20, "code,asc"), "PRJ-INT", null,
                ownerActor);
        assertThat(ownerProjects.content()).extracting("code").containsExactly("PRJ-INT");

        AppUser outsider = createUser("project.outsider", "USER");
        CurrentUser outsiderActor = actor(outsider, "USER");
        var outsiderProjects = projectService.list(PageQuery.of(0, 20, "code,asc"), "PRJ-INT", null,
                outsiderActor);
        assertThat(outsiderProjects.content()).isEmpty();
        assertThatThrownBy(() -> projectService.get(created.project().id(), outsiderActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));

        var duplicateAdd = projectService.addMembers(created.project().id(),
                new AddMembersRequest(List.of(owner.getId(), member.getId())), ownerActor);
        assertThat(duplicateAdd.added()).isZero();
        assertThat(duplicateAdd.alreadyMembers()).isEqualTo(2);

        var replay = projectService.create(new CreateProjectRequest("PRJ-INT", "Ignored",
                null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30), requestId, null), ownerActor);
        assertThat(replay.replay()).isTrue();

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("PRJ-INT", "Duplicate",
                null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 6, 30), UUID.randomUUID(), null),
                ownerActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_CODE_TAKEN));
        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest("PRJ-BAD", "Bad dates",
                null, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 1, 5), UUID.randomUUID(), null),
                ownerActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));

        var updated = projectService.update(created.project().id(),
                new UpdateProjectRequest("Renamed project", "Updated", LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 7, 31)), ownerActor);
        assertThat(updated.name()).isEqualTo("Renamed project");

        projectService.removeMember(created.project().id(), member.getId(), ownerActor);
        assertThatThrownBy(() -> projectService.removeMember(created.project().id(), member.getId(),
                ownerActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        var archived = projectService.changeStatus(created.project().id(), ProjectStatus.ARCHIVED,
                actor(owner, "ADMIN"));
        assertThat(archived.status()).isEqualTo(ProjectStatus.ARCHIVED);
        var restored = projectService.changeStatus(created.project().id(), ProjectStatus.ACTIVE,
                actor(owner, "ADMIN"));
        assertThat(restored.status()).isEqualTo(ProjectStatus.ACTIVE);

        assertThatThrownBy(() -> projectService.get(UUID.randomUUID(), ownerActor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    private RequestPostProcessor jwtFor(AppUser user, String... roles) {
        return jwt().jwt(jwt -> jwt
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("fullName", user.getFullName())
                .claim("roles", List.of(roles)))
                .authorities(java.util.Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toArray(SimpleGrantedAuthority[]::new));
    }

    @Test
    void publishesGeneratedOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"openapi\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/projects")));
    }

    @Test
    void httpControllerContractsUseProblemDetailsAndRoleGuards() throws Exception {
        AppUser admin = createUser("http.admin", "ADMIN");
        AppUser member = createUser("http.member", "USER");

        mockMvc.perform(get("/api/v1/users/me").with(jwtFor(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("http.member"))
                .andExpect(jsonPath("$.roles").isArray());

        mockMvc.perform(get("/api/v1/users").with(jwtFor(member, "USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users?page=0&size=5").with(jwtFor(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/projects/not-a-uuid").with(jwtFor(member, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        String code = "HTTP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = """
                {"code":"%s","name":"HTTP project","description":"Slice test",
                 "startDate":"2026-01-01","endDate":"2026-03-31","memberIds":["%s"],
                 "requestId":"%s"}
                """.formatted(code, member.getId(), UUID.randomUUID());
        var first = mockMvc.perform(post("/api/v1/projects")
                        .with(jwtFor(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn();
        String projectId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/projects/" + projectId).with(jwtFor(member, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        mockMvc.perform(post("/api/v1/projects")
                        .with(jwtFor(member, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
