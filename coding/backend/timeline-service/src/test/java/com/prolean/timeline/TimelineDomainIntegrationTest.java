package com.prolean.timeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.prolean.timeline.domain.HolidayType;
import com.prolean.timeline.domain.LifecycleStatus;
import com.prolean.timeline.domain.OperationalStatus;
import com.prolean.timeline.dto.HolidayDtos.HolidayRequest;
import com.prolean.timeline.dto.TimelineDtos.CreateRevisionRequest;
import com.prolean.timeline.dto.TimelineDtos.CreateTimelineRequest;
import com.prolean.timeline.dto.TimelineDtos.EditDraftRequest;
import com.prolean.timeline.dto.TimelineDtos.PublishRequest;
import com.prolean.timeline.repository.HolidayRepository;
import com.prolean.timeline.repository.TimelineRepository;
import com.prolean.timeline.repository.TimelineVersionRepository;
import com.prolean.timeline.service.HolidayService;
import com.prolean.timeline.service.TimelineService;
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
class TimelineDomainIntegrationTest {

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
    TimelineService timelineService;
    @Autowired
    HolidayService holidayService;
    @Autowired
    TimelineRepository timelines;
    @Autowired
    TimelineVersionRepository versions;
    @Autowired
    HolidayRepository holidays;
    @Autowired
    MockMvc mockMvc;

    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final CurrentUser actor = new CurrentUser(actorId, "timeline.user", "Timeline User",
            Set.of("USER"));

    private void stubAccess(UUID project, boolean allowed, String status) {
        USER.stubFor(get(urlPathEqualTo("/internal/projects/" + project + "/access"))
                .withQueryParam("userId", equalTo(actorId.toString()))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"projectId":"%s","userId":"%s","allowed":%s,"member":%s,"admin":false,
                                 "projectStatus":"%s","projectCode":"INT-TL"}
                                """.formatted(project, actorId, allowed, allowed, status))));
    }

    private CreateTimelineRequest createRequest(UUID requestId) {
        return new CreateTimelineRequest(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 16),
                requestId, "Initial baseline");
    }

    @Test
    void draftPublishRevisionSnapshotAndReplay() {
        stubAccess(projectId, true, "ACTIVE");
        UUID createRequestId = UUID.randomUUID();
        var created = timelineService.create(projectId, createRequest(createRequestId), actor);

        assertThat(created.replay()).isFalse();
        assertThat(created.version().lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
        assertThat(created.version().durationWorkdays()).isEqualTo(9);
        assertThat(created.version().versionNumber()).isOne();

        var replay = timelineService.create(projectId, createRequest(createRequestId), actor);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.version().id()).isEqualTo(created.version().id());

        var edited = timelineService.editDraft(projectId,
                new EditDraftRequest(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 15),
                        "Adjusted after review", created.version().updatedAt()), actor);
        assertThat(edited.startDate()).isEqualTo(LocalDate.of(2026, 1, 6));
        assertThat(edited.endDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(edited.durationWorkdays()).isEqualTo(8);

        var aggregate = timelineService.aggregate(projectId, actor);
        assertThat(aggregate.draft()).isNotNull();
        assertThat(aggregate.current()).isNull();

        var published = timelineService.publish(projectId,
                new PublishRequest(UUID.randomUUID(), aggregate.draft().updatedAt(), null), actor);
        assertThat(published.version().lifecycleStatus()).isEqualTo(LifecycleStatus.PUBLISHED);
        assertThat(published.version().percentActual()).isZero();

        aggregate = timelineService.aggregate(projectId, actor);
        assertThat(aggregate.current().versionNumber()).isEqualTo(1);
        assertThat(aggregate.draft()).isNull();
        String firstVersionId = aggregate.current().id().toString();

        var revision = timelineService.createRevision(projectId,
                new CreateRevisionRequest(null, null, UUID.randomUUID(), "Revise baseline"), actor);
        assertThat(revision.version().versionNumber()).isEqualTo(2);
        assertThat(revision.version().startDate()).isEqualTo(aggregate.current().startDate());
        assertThat(revision.version().revisionNotes()).isEqualTo("Revise baseline");

        aggregate = timelineService.aggregate(projectId, actor);
        var revisedDraft = timelineService.editDraft(projectId,
                new EditDraftRequest(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 13),
                        "Feb window", aggregate.draft().updatedAt()), actor);
        assertThat(revisedDraft.versionNumber()).isEqualTo(2);

        aggregate = timelineService.aggregate(projectId, actor);
        var publishedRevision = timelineService.publish(projectId,
                new PublishRequest(UUID.randomUUID(), aggregate.draft().updatedAt(),
                        aggregate.current().updatedAt()), actor);
        assertThat(publishedRevision.version().versionNumber()).isEqualTo(2);
        assertThat(publishedRevision.version().lifecycleStatus()).isEqualTo(LifecycleStatus.PUBLISHED);

        var all = versions.findByTimelineId(aggregate.timelineId(), PageQuery.of(0, 10, null)
                .toPageable(Set.of("versionNumber"), "versionNumber,desc"));
        assertThat(all.getTotalElements()).isEqualTo(2);
        var first = versions.findById(UUID.fromString(firstVersionId)).orElseThrow();
        assertThat(first.getLifecycleStatus()).isEqualTo(LifecycleStatus.SUPERSEDED);
        assertThat(first.getSnapshotDurationWorkdays()).isEqualTo(8);
        assertThat(first.getSnapshotPercentActual()).isZero();
        assertThat(first.getSnapshotAsOfDate()).isNotNull();
        assertThat(first.getSupersededBy()).isEqualTo(actorId);
        assertThat(first.getSupersededAt()).isNotNull();
    }

    @Test
    void cancelBehaviourAndAccessErrors() {
        UUID cancelProject = UUID.randomUUID();
        stubAccess(cancelProject, true, "ACTIVE");
        var created = timelineService.create(cancelProject, createRequest(UUID.randomUUID()), actor);
        timelineService.cancelDraft(cancelProject, created.version().updatedAt(), actor);
        assertThat(timelines.findById(created.version().timelineId())).isEmpty();
        assertThatThrownBy(() -> timelineService.aggregate(cancelProject, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));

        stubAccess(projectId, false, "ACTIVE");
        assertThatThrownBy(() -> timelineService.aggregate(projectId, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));

        stubAccess(projectId, true, "ARCHIVED");
        assertThatThrownBy(() -> timelineService.create(projectId, createRequest(UUID.randomUUID()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED));

        UUID missing = UUID.randomUUID();
        stubAccess(missing, true, "ACTIVE");
        assertThatThrownBy(() -> timelineService.aggregate(missing, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));
    }

    @Test
    void holidayCrudAndSeedCoverage() {
        long seeded = holidays.count();
        assertThat(seeded).isGreaterThanOrEqualTo(78);

        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin.holiday", "Admin",
                Set.of("ADMIN"));
        LocalDate date = LocalDate.of(2028, 12, 31);
        var created = holidayService.create(new HolidayRequest(date, "Integration holiday",
                HolidayType.JOINT_LEAVE), admin);
        assertThat(created.name()).isEqualTo("Integration holiday");
        assertThatThrownBy(() -> holidayService.create(
                new HolidayRequest(date, "Duplicate", HolidayType.NATIONAL), admin))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.HOLIDAY_DATE_TAKEN));

        var updated = holidayService.update(created.id(),
                new HolidayRequest(date, "Renamed holiday", HolidayType.NATIONAL), admin);
        assertThat(updated.name()).isEqualTo("Renamed holiday");
        assertThat(updated.type()).isEqualTo(HolidayType.NATIONAL);

        var page = holidayService.list(2028, PageQuery.of(0, 20, "date,asc"));
        assertThat(page.content()).extracting("name").contains("Renamed holiday");

        holidayService.delete(created.id());
        assertThat(holidays.findById(created.id())).isEmpty();
        assertThatThrownBy(() -> holidayService.delete(created.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.HOLIDAY_NOT_FOUND));
    }

    @Test
    void invalidDatesCoverageAndNoWorkingDaysAreRejected() {
        stubAccess(projectId, true, "ACTIVE");
        assertThatThrownBy(() -> timelineService.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                        UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));

        assertThatThrownBy(() -> timelineService.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5),
                        UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.HOLIDAY_COVERAGE_REQUIRED));

        // A weekend-only range has zero working days.
        assertThatThrownBy(() -> timelineService.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                        UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.NO_WORKING_DAYS));

        assertThatThrownBy(() -> timelineService.changeProgress(projectId, 50, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));
    }

    @Test
    void horizonHeadersAndWriteBoundaryAgree() throws Exception {
        UUID project = UUID.randomUUID();
        stubAccess(project, true, "ACTIVE");
        var auth = jwt().jwt(jwt -> jwt
                .subject(actorId.toString())
                .claim("username", actor.username())
                .claim("fullName", actor.fullName())
                .claim("roles", java.util.List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/holidays").with(auth))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Holiday-Coverage-Start", "2025-01-01"))
                .andExpect(header().string("X-Holiday-Coverage-End", "2027-12-31"));

        var created = timelineService.create(project,
                new CreateTimelineRequest(LocalDate.of(2027, 12, 31), LocalDate.of(2027, 12, 31),
                        UUID.randomUUID(), null),
                actor);
        assertThat(created.version().durationWorkdays()).isEqualTo(1);
    }

    @Test
    void publishesGeneratedOpenApiDocument() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"openapi\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/projects/{projectId}/timeline")));
    }

    @Test
    void httpContractsCreateDraftAndReturnProblemDetails() throws Exception {
        UUID httpProject = UUID.randomUUID();
        stubAccess(httpProject, true, "ACTIVE");
        var auth = jwt().jwt(jwt -> jwt
                .subject(actorId.toString())
                .claim("username", actor.username())
                .claim("fullName", actor.fullName())
                .claim("roles", java.util.List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + httpProject + "/timeline").with(auth))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TIMELINE_NOT_FOUND"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + httpProject + "/timeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        String requestId = UUID.randomUUID().toString();
        String body = """
                {"startDate":"2026-03-02","endDate":"2026-03-13",
                 "requestId":"%s","revisionNotes":"HTTP draft"}
                """.formatted(requestId);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects/" + httpProject + "/timeline")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.durationWorkdays").value(10));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects/" + httpProject + "/timeline")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/projects/" + httpProject + "/timeline")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/projects/" + httpProject + "/timeline").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.versionNumber").value(1));
    }
}
