package com.prolean.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prolean.common.api.PageQuery;
import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import com.prolean.common.security.CurrentUser;
import com.prolean.timeline.client.TimelineAccessClient;
import com.prolean.timeline.config.TimelineProperties;
import com.prolean.timeline.domain.Holiday;
import com.prolean.timeline.domain.LifecycleStatus;
import com.prolean.timeline.domain.OperationalStatus;
import com.prolean.timeline.domain.Timeline;
import com.prolean.timeline.domain.TimelineEventType;
import com.prolean.timeline.domain.TimelineVersion;
import com.prolean.timeline.domain.TimelineVersionEvent;
import com.prolean.timeline.dto.TimelineDtos.CreateRevisionRequest;
import com.prolean.timeline.dto.TimelineDtos.CreateTimelineRequest;
import com.prolean.timeline.dto.TimelineDtos.EditDraftRequest;
import com.prolean.timeline.dto.TimelineDtos.PublishRequest;
import com.prolean.timeline.dto.TimelineDtos.TimelineAggregateResponse;
import com.prolean.timeline.dto.TimelineDtos.TimelineVersionResponse;
import com.prolean.timeline.dto.TimelineDtos.VersionResult;
import com.prolean.timeline.repository.HolidayRepository;
import com.prolean.timeline.repository.TimelineRepository;
import com.prolean.timeline.repository.TimelineVersionEventRepository;
import com.prolean.timeline.repository.TimelineVersionRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 2);
    private static final LocalDate END = LocalDate.of(2026, 11, 6);

    @Mock
    private TimelineRepository timelines;
    @Mock
    private TimelineVersionRepository versions;
    @Mock
    private TimelineVersionEventRepository events;
    @Mock
    private HolidayRepository holidays;
    @Mock
    private TimelineAccessClient accessClient;

    private TimelineProperties properties;
    private TimelineService service;
    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final CurrentUser actor = new CurrentUser(actorId, "member", "Member", Set.of("USER"));
    private final TimelineAccessClient.Access activeAccess =
            new TimelineAccessClient.Access(projectId, actorId, true, true, false, "ACTIVE", "ALPHA");

    @BeforeEach
    void setUp() {
        properties = new TimelineProperties();
        service = new TimelineService(timelines, versions, events, holidays, accessClient, properties);

        lenient().when(accessClient.access(any(), any())).thenReturn(activeAccess);
        lenient().when(holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any()))
                .thenReturn(List.of());
        lenient().when(holidays.findMinimumHolidayDate()).thenReturn(LocalDate.of(2020, 1, 1));
        lenient().when(holidays.findMaximumHolidayDate()).thenReturn(LocalDate.of(2030, 12, 31));
        lenient().when(versions.save(any())).thenAnswer(invocation -> {
            TimelineVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(UUID.randomUUID());
            }
            return version;
        });
        lenient().when(timelines.save(any())).thenAnswer(invocation -> {
            Timeline timeline = invocation.getArgument(0);
            if (timeline.getId() == null) {
                timeline.setId(UUID.randomUUID());
            }
            return timeline;
        });
    }

    private Timeline timeline() {
        Timeline timeline = new Timeline();
        timeline.setId(UUID.randomUUID());
        timeline.setProjectId(projectId);
        timeline.setRequestId(UUID.randomUUID());
        timeline.setCreatedBy(actorId);
        return timeline;
    }

    private TimelineVersion version(Timeline timeline, int number, LifecycleStatus lifecycle) {
        TimelineVersion version = new TimelineVersion();
        version.setId(UUID.randomUUID());
        version.setTimelineId(timeline == null ? UUID.randomUUID() : timeline.getId());
        version.setVersionNumber(number);
        version.setLifecycleStatus(lifecycle);
        version.setStartDate(START);
        version.setEndDate(END);
        version.setOperationalStatus(OperationalStatus.OPEN);
        version.setPercentActual(0);
        version.setRequestId(UUID.randomUUID());
        version.setCreatedBy(actorId);
        version.setUpdatedBy(actorId);
        return version;
    }

    private void stubTimeline(Timeline timeline) {
        when(timelines.findById(timeline.getId())).thenReturn(Optional.of(timeline));
        when(timelines.findByProjectId(projectId)).thenReturn(Optional.of(timeline));
        when(timelines.findByProjectIdForUpdate(projectId)).thenReturn(Optional.of(timeline));
    }

    private void stubVersion(Timeline timeline, LifecycleStatus status, TimelineVersion version) {
        when(versions.findByTimelineIdAndLifecycleStatus(timeline.getId(), status))
                .thenReturn(Optional.ofNullable(version));
    }

    private void setUpdatedAt(TimelineVersion version, Instant instant) {
        ReflectionTestUtils.setField(version, "updatedAt", instant);
    }

    @Test
    void aggregateReturnsCurrentDraftAndCount() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 2, LifecycleStatus.PUBLISHED);
        TimelineVersion draft = version(timeline, 3, LifecycleStatus.DRAFT);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        when(versions.countByTimelineId(timeline.getId())).thenReturn(3L);

        TimelineAggregateResponse result = service.aggregate(projectId, actor);

        assertThat(result.timelineId()).isEqualTo(timeline.getId());
        assertThat(result.versionCount()).isEqualTo(3);
        assertThat(result.current().lifecycleStatus()).isEqualTo(LifecycleStatus.PUBLISHED);
        assertThat(result.draft().lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
    }

    @Test
    void aggregateHandlesTimelineWithoutVersions() {
        Timeline timeline = timeline();
        stubTimeline(timeline);
        when(versions.countByTimelineId(timeline.getId())).thenReturn(0L);

        TimelineAggregateResponse result = service.aggregate(projectId, actor);

        assertThat(result.current()).isNull();
        assertThat(result.draft()).isNull();
    }

    @Test
    void currentUsesSuppliedAsOfAndRejectsMissingCurrent() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);

        TimelineVersionResponse result = service.current(projectId, LocalDate.of(2026, 11, 4), actor);
        assertThat(result.percentPlan()).isEqualByComparingTo("60.00");
        assertThat(result.planAsOfDate()).isEqualTo(LocalDate.of(2026, 11, 4));

        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        assertThatThrownBy(() -> service.current(projectId, null, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_CURRENT_VERSION));
    }

    @Test
    void versionsMapsPageAndRejectsMissingTimeline() {
        Timeline timeline = timeline();
        TimelineVersion v = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        when(versions.findByTimelineId(eq(timeline.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(v), PageRequest.of(0, 20), 1));

        var page = service.versions(projectId, PageQuery.of(0, 20, null), actor);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);

        when(timelines.findByProjectId(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.versions(projectId, PageQuery.of(0, 20, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));
    }

    @Test
    void versionReturnsMatchingVersion() {
        Timeline timeline = timeline();
        TimelineVersion v = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        when(versions.findByIdAndTimelineId(v.getId(), timeline.getId())).thenReturn(Optional.of(v));

        assertThat(service.version(projectId, v.getId(), actor).id()).isEqualTo(v.getId());

        when(versions.findByIdAndTimelineId(v.getId(), timeline.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.version(projectId, v.getId(), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));
    }

    @Test
    void createCreatesTimelineAndFirstDraft() {
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        when(timelines.findByProjectId(projectId)).thenReturn(Optional.empty());

        UUID requestId = UUID.randomUUID();
        VersionResult result = service.create(projectId,
                new CreateTimelineRequest(START, END, requestId, "Initial plan"), actor);

        assertThat(result.replay()).isFalse();
        assertThat(result.version().versionNumber()).isEqualTo(1);
        assertThat(result.version().lifecycleStatus()).isEqualTo(LifecycleStatus.DRAFT);
        assertThat(result.version().durationWorkdays()).isEqualTo(5);
        assertThat(result.version().revisionNotes()).isEqualTo("Initial plan");
        verify(timelines).save(any());
        verify(versions).save(any());
        ArgumentCaptor<TimelineVersionEvent> eventCaptor =
                ArgumentCaptor.forClass(TimelineVersionEvent.class);
        verify(events).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(TimelineEventType.CREATED);
    }

    @Test
    void createUsesWholeCalendarYearsForHolidayCoverage() {
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        when(timelines.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(holidays.findMinimumHolidayDate()).thenReturn(LocalDate.of(2025, 6, 17));
        when(holidays.findMaximumHolidayDate()).thenReturn(LocalDate.of(2027, 12, 26));

        VersionResult result = service.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2027, 12, 31), LocalDate.of(2027, 12, 31),
                        UUID.randomUUID(), null),
                actor);

        assertThat(result.version().durationWorkdays()).isEqualTo(1);

        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 1),
                        UUID.randomUUID(), null),
                actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_COVERAGE_REQUIRED));
    }

    @Test
    void createReplaysSameRequestAndRejectsAnotherProject() {
        Timeline existingTimeline = timeline();
        TimelineVersion existing = version(existingTimeline, 1, LifecycleStatus.DRAFT);
        existing.setRequestId(UUID.randomUUID());
        when(versions.findByRequestId(existing.getRequestId())).thenReturn(Optional.of(existing));
        when(timelines.findById(existingTimeline.getId())).thenReturn(Optional.of(existingTimeline));

        VersionResult replay = service.create(projectId,
                new CreateTimelineRequest(START, END, existing.getRequestId(), null), actor);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.version().id()).isEqualTo(existing.getId());
        verify(versions, never()).save(existing);

        existingTimeline.setProjectId(UUID.randomUUID());
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(START, END, existing.getRequestId(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DATA_CONFLICT));
    }

    @Test
    void createRejectsExistingTimelineAndInvalidRanges() {
        Timeline existingTimeline = timeline();
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        when(timelines.findByProjectId(projectId)).thenReturn(Optional.of(existingTimeline));
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(START, END, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMELINE_EXISTS));

        when(timelines.findByProjectId(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(END, START, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));

        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(LocalDate.of(2019, 1, 1), END, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.HOLIDAY_COVERAGE_REQUIRED));

        when(holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(START, END))
                .thenReturn(List.of(holiday(START), holiday(START.plusDays(1)), holiday(START.plusDays(2)),
                        holiday(START.plusDays(3)), holiday(START.plusDays(4))));
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(START, END, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_WORKING_DAYS));
    }

    @Test
    void createChecksAccessAndArchivedState() {
        when(accessClient.access(any(), any())).thenReturn(null);
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(START, END, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        when(accessClient.access(any(), any())).thenReturn(
                new TimelineAccessClient.Access(projectId, actorId, true, true, false, "ARCHIVED", "ALPHA"));
        assertThatThrownBy(() -> service.create(projectId,
                new CreateTimelineRequest(START, END, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED));

        when(accessClient.access(any(), any())).thenReturn(
                new TimelineAccessClient.Access(projectId, actorId, false, false, false, "ACTIVE", "ALPHA"));
        assertThatThrownBy(() -> service.aggregate(projectId, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void editDraftUpdatesDatesNotesAndTimestamp() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 2, LifecycleStatus.DRAFT);
        setUpdatedAt(draft, Instant.parse("2026-10-20T10:00:00.123456Z"));
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);

        TimelineVersionResponse result = service.editDraft(projectId,
                new EditDraftRequest(START.plusDays(1), END.plusDays(1), "Changed",
                        Instant.parse("2026-10-20T10:00:00.123456Z")), actor);

        assertThat(result.startDate()).isEqualTo(START.plusDays(1));
        assertThat(result.endDate()).isEqualTo(END.plusDays(1));
        assertThat(result.revisionNotes()).isEqualTo("Changed");
        verify(versions).save(draft);
    }

    @Test
    void editDraftRejectsTimestampMismatchMissingDraftAndInvalidRange() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 2, LifecycleStatus.DRAFT);
        setUpdatedAt(draft, Instant.parse("2026-10-20T10:00:00Z"));
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);

        assertThatThrownBy(() -> service.editDraft(projectId,
                new EditDraftRequest(START, END, null, Instant.parse("2026-10-20T10:00:01Z")), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_MODIFIED));

        assertThatThrownBy(() -> service.editDraft(projectId,
                new EditDraftRequest(END, START, null, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));

        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        assertThatThrownBy(() -> service.editDraft(projectId,
                new EditDraftRequest(START, END, null, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_NOT_FOUND));
    }

    @Test
    void cancelFirstDraftDeletesTimelineIdentity() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 1, LifecycleStatus.DRAFT);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        when(versions.existsByTimelineIdAndLifecycleStatusIn(eq(timeline.getId()), any()))
                .thenReturn(false);
        when(versions.countByTimelineId(timeline.getId())).thenReturn(1L);

        service.cancelDraft(projectId, null, actor);

        verify(timelines).delete(timeline);
        verify(versions, never()).save(draft);
    }

    @Test
    void cancelLaterDraftFrozenSnapshotAndChecksTimestamp() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 2, LifecycleStatus.DRAFT);
        setUpdatedAt(draft, Instant.parse("2026-10-20T10:00:00Z"));
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        when(versions.existsByTimelineIdAndLifecycleStatusIn(eq(timeline.getId()), any()))
                .thenReturn(true);
        when(versions.countByTimelineId(timeline.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.cancelDraft(projectId,
                Instant.parse("2026-10-20T09:00:00Z"), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_MODIFIED));

        service.cancelDraft(projectId, Instant.parse("2026-10-20T10:00:00Z"), actor);

        assertThat(draft.getLifecycleStatus()).isEqualTo(LifecycleStatus.CANCELLED);
        assertThat(draft.getCancelledBy()).isEqualTo(actorId);
        assertThat(draft.getSnapshotDurationWorkdays()).isEqualTo(5);
        assertThat(draft.getSnapshotPercentPlan()).isEqualByComparingTo("0.00");
        assertThat(draft.getSnapshotPercentActual()).isZero();
        assertThat(draft.getSnapshotAsOfDate()).isNotNull();
        verify(versions).save(draft);
    }

    @Test
    void cancelMissingDraftFails() {
        Timeline timeline = timeline();
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        assertThatThrownBy(() -> service.cancelDraft(projectId, null, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_NOT_FOUND));
    }

    @Test
    void publishInitialDraftSetsPublishedFields() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 1, LifecycleStatus.DRAFT);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        when(versions.findByPublishRequestId(any())).thenReturn(Optional.empty());

        UUID publishRequest = UUID.randomUUID();
        VersionResult result = service.publish(projectId,
                new PublishRequest(publishRequest, null, null), actor);

        assertThat(result.replay()).isFalse();
        assertThat(result.version().lifecycleStatus()).isEqualTo(LifecycleStatus.PUBLISHED);
        assertThat(result.version().operationalStatus()).isEqualTo(OperationalStatus.OPEN);
        assertThat(result.version().percentActual()).isZero();
        assertThat(draft.getPublishedBy()).isEqualTo(actorId);
        assertThat(draft.getPublishedAt()).isNotNull();
        assertThat(draft.getPublishRequestId()).isEqualTo(publishRequest);
        verify(versions).save(draft);
        verify(events).save(any());
    }

    @Test
    void publishSupersedesCurrentAndCarriesOperationalSnapshot() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 2, LifecycleStatus.DRAFT);
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        current.setStartDate(LocalDate.of(2026, 10, 1));
        current.setEndDate(LocalDate.of(2026, 10, 7));
        current.setOperationalStatus(OperationalStatus.IN_PROGRESS);
        current.setPercentActual(42);
        setUpdatedAt(current, Instant.parse("2026-10-20T10:00:00Z"));
        setUpdatedAt(draft, Instant.parse("2026-10-20T11:00:00Z"));
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        when(versions.findByPublishRequestId(any())).thenReturn(Optional.empty());

        VersionResult result = service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), Instant.parse("2026-10-20T11:00:00Z"),
                        Instant.parse("2026-10-20T10:00:00Z")), actor);

        assertThat(result.version().lifecycleStatus()).isEqualTo(LifecycleStatus.PUBLISHED);
        assertThat(result.version().operationalStatus()).isEqualTo(OperationalStatus.IN_PROGRESS);
        assertThat(result.version().percentActual()).isEqualTo(42);
        assertThat(current.getLifecycleStatus()).isEqualTo(LifecycleStatus.SUPERSEDED);
        assertThat(current.getSupersededBy()).isEqualTo(actorId);
        assertThat(current.getSupersededAt()).isNotNull();
        assertThat(current.getSnapshotDurationWorkdays()).isEqualTo(5);
        assertThat(current.getSnapshotPercentPlan()).isEqualByComparingTo("100.00");
        assertThat(current.getSnapshotPercentActual()).isEqualTo(42);
        verify(versions).save(current);
        verify(versions).save(draft);
    }

    @Test
    void publishReplayRejectsAnotherProject() {
        Timeline timeline = timeline();
        TimelineVersion published = version(timeline, 1, LifecycleStatus.PUBLISHED);
        when(versions.findByPublishRequestId(any())).thenReturn(Optional.of(published));
        when(timelines.findById(timeline.getId())).thenReturn(Optional.of(timeline));

        var replay = service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), null, null), actor);
        assertThat(replay.replay()).isTrue();

        timeline.setProjectId(UUID.randomUUID());
        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), null, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DATA_CONFLICT));
    }

    @Test
    void publishRejectsMissingDraftAndChangedVersions() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 2, LifecycleStatus.DRAFT);
        setUpdatedAt(draft, Instant.parse("2026-10-20T10:00:00Z"));
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        setUpdatedAt(current, Instant.parse("2026-10-19T10:00:00Z"));
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        when(versions.findByPublishRequestId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), null, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_NOT_FOUND));

        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), Instant.parse("2026-10-20T11:00:00Z"), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_MODIFIED));

        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), Instant.parse("2026-10-20T10:00:00Z"), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CURRENT_VERSION_CHANGED));

        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), Instant.parse("2026-10-20T10:00:00Z"),
                        Instant.parse("2026-10-18T10:00:00Z")), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CURRENT_VERSION_CHANGED));
    }

    @Test
    void publishWithoutCurrentRejectsUnexpectedCurrentTimestamp() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 1, LifecycleStatus.DRAFT);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        when(versions.findByPublishRequestId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(projectId,
                new PublishRequest(UUID.randomUUID(), null, Instant.now()), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CURRENT_VERSION_CHANGED));
    }

    @Test
    void createRevisionCopiesCurrentDatesAndNotes() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        current.setRevisionNotes("Old notes");
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        when(versions.maxVersionNumber(timeline.getId())).thenReturn(1);

        VersionResult result = service.createRevision(projectId,
                new CreateRevisionRequest(null, null, UUID.randomUUID(), null), actor);

        assertThat(result.replay()).isFalse();
        assertThat(result.version().versionNumber()).isEqualTo(2);
        assertThat(result.version().startDate()).isEqualTo(START);
        assertThat(result.version().endDate()).isEqualTo(END);
        assertThat(result.version().revisionNotes()).isEqualTo("Old notes");
        verify(versions).save(any());
    }

    @Test
    void createRevisionUsesOverridesAndReplayRules() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        when(versions.maxVersionNumber(timeline.getId())).thenReturn(1);

        VersionResult result = service.createRevision(projectId,
                new CreateRevisionRequest(START.plusDays(1), END.plusDays(1), UUID.randomUUID(), "New"),
                actor);
        assertThat(result.version().startDate()).isEqualTo(START.plusDays(1));
        assertThat(result.version().revisionNotes()).isEqualTo("New");

        TimelineVersion replay = version(timeline, 2, LifecycleStatus.DRAFT);
        replay.setRequestId(UUID.randomUUID());
        when(versions.findByRequestId(replay.getRequestId())).thenReturn(Optional.of(replay));
        when(timelines.findById(timeline.getId())).thenReturn(Optional.of(timeline));
        assertThat(service.createRevision(projectId,
                new CreateRevisionRequest(null, null, replay.getRequestId(), null), actor).replay())
                .isTrue();

        timeline.setProjectId(UUID.randomUUID());
        when(timelines.findById(replay.getTimelineId())).thenReturn(Optional.of(timeline));
        assertThatThrownBy(() -> service.createRevision(projectId,
                new CreateRevisionRequest(null, null, replay.getRequestId(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DATA_CONFLICT));
    }

    @Test
    void createRevisionRejectsMissingCurrentDraftAndInvalidRange() {
        Timeline timeline = timeline();
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        when(versions.findByRequestId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createRevision(projectId,
                new CreateRevisionRequest(null, null, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_CURRENT_VERSION));

        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        stubVersion(timeline, LifecycleStatus.DRAFT, version(timeline, 2, LifecycleStatus.DRAFT));
        assertThatThrownBy(() -> service.createRevision(projectId,
                new CreateRevisionRequest(null, null, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DRAFT_EXISTS));

        stubVersion(timeline, LifecycleStatus.DRAFT, null);
        assertThatThrownBy(() -> service.createRevision(projectId,
                new CreateRevisionRequest(END, START, UUID.randomUUID(), null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    void changeStatusValidatesTransitionsAndClosesAtHundred() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        current.setPercentActual(25);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);

        TimelineVersionResponse inProgress = service.changeStatus(projectId,
                OperationalStatus.IN_PROGRESS, actor);
        assertThat(inProgress.operationalStatus()).isEqualTo(OperationalStatus.IN_PROGRESS);

        TimelineVersionResponse closed = service.changeStatus(projectId,
                OperationalStatus.CLOSED, actor);
        assertThat(closed.operationalStatus()).isEqualTo(OperationalStatus.CLOSED);
        assertThat(closed.percentActual()).isEqualTo(100);

        TimelineVersionResponse reopened = service.changeStatus(projectId,
                OperationalStatus.IN_PROGRESS, actor);
        assertThat(reopened.operationalStatus()).isEqualTo(OperationalStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.changeStatus(projectId, OperationalStatus.OPEN, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));

        assertThat(service.changeStatus(projectId, OperationalStatus.IN_PROGRESS, actor)
                .operationalStatus()).isEqualTo(OperationalStatus.IN_PROGRESS);
    }

    @Test
    void mutationPathsRecheckAccessBeforeCommit() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        TimelineAccessClient.Access denied =
                new TimelineAccessClient.Access(projectId, actorId, false, false, false, "ACTIVE", "ALPHA");
        when(accessClient.access(any(), any())).thenReturn(activeAccess, denied);

        assertThatThrownBy(() -> service.changeStatus(projectId, OperationalStatus.IN_PROGRESS, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        when(accessClient.access(any(), any())).thenReturn(activeAccess, denied);
        assertThatThrownBy(() -> service.changeProgress(projectId, 10, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void cancelRechecksAccessBeforeDeletingInitialDraft() {
        Timeline timeline = timeline();
        TimelineVersion draft = version(timeline, 1, LifecycleStatus.DRAFT);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.DRAFT, draft);
        TimelineAccessClient.Access denied =
                new TimelineAccessClient.Access(projectId, actorId, false, false, false, "ACTIVE", "ALPHA");
        when(accessClient.access(any(), any())).thenReturn(activeAccess, denied);

        assertThatThrownBy(() -> service.cancelDraft(projectId, null, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(timelines, never()).delete(any());
    }

    @Test
    void changeStatusRejectsMissingCurrent() {
        Timeline timeline = timeline();
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        assertThatThrownBy(() -> service.changeStatus(projectId, OperationalStatus.IN_PROGRESS, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_CURRENT_VERSION));
    }

    @Test
    void changeProgressUpdatesOpenAndRejectsClosed() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);

        assertThat(service.changeProgress(projectId, 55, actor).percentActual()).isEqualTo(55);

        current.setOperationalStatus(OperationalStatus.CLOSED);
        current.setPercentActual(100);
        assertThatThrownBy(() -> service.changeProgress(projectId, 20, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMELINE_CLOSED));

        stubVersion(timeline, LifecycleStatus.PUBLISHED, null);
        assertThatThrownBy(() -> service.changeProgress(projectId, 20, actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_CURRENT_VERSION));
    }

    @Test
    void readsUseSnapshotValuesForFrozenVersions() {
        Timeline timeline = timeline();
        TimelineVersion frozen = version(timeline, 1, LifecycleStatus.SUPERSEDED);
        frozen.setSnapshotDurationWorkdays(4);
        frozen.setSnapshotPercentPlan(new BigDecimal("75.50"));
        frozen.setSnapshotPercentActual(80);
        frozen.setSnapshotAsOfDate(LocalDate.of(2026, 9, 1));
        when(versions.findByIdAndTimelineId(frozen.getId(), timeline.getId()))
                .thenReturn(Optional.of(frozen));
        stubTimeline(timeline);

        TimelineVersionResponse response = service.version(projectId, frozen.getId(), actor);

        assertThat(response.durationWorkdays()).isEqualTo(4);
        assertThat(response.percentPlan()).isEqualByComparingTo("75.50");
        assertThat(response.percentActual()).isEqualTo(80);
        assertThat(response.planAsOfDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.planWarning()).isNull();
    }

    @Test
    void frozenVersionWithZeroDurationReportsWarning() {
        Timeline timeline = timeline();
        TimelineVersion frozen = version(timeline, 1, LifecycleStatus.CANCELLED);
        frozen.setSnapshotDurationWorkdays(0);
        frozen.setSnapshotPercentPlan(BigDecimal.ZERO.setScale(2));
        frozen.setSnapshotPercentActual(0);
        frozen.setSnapshotAsOfDate(LocalDate.of(2026, 9, 1));
        when(versions.findByIdAndTimelineId(frozen.getId(), timeline.getId()))
                .thenReturn(Optional.of(frozen));
        stubTimeline(timeline);

        assertThat(service.version(projectId, frozen.getId(), actor).planWarning())
                .isEqualTo(WorkingDayCalculator.NO_WORKING_DAYS);
    }

    @Test
    void lockTimelineMissingFails() {
        when(timelines.findByProjectIdForUpdate(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.editDraft(projectId,
                new EditDraftRequest(START, END, null, null), actor))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TIMELINE_NOT_FOUND));
    }

    @Test
    void holidayLookupParticipatesInPlanCalculation() {
        Timeline timeline = timeline();
        TimelineVersion current = version(timeline, 1, LifecycleStatus.PUBLISHED);
        stubTimeline(timeline);
        stubVersion(timeline, LifecycleStatus.PUBLISHED, current);
        when(holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(START, END))
                .thenReturn(List.of(holiday(START), holiday(START.plusDays(1))));

        TimelineVersionResponse response = service.current(projectId, START.plusDays(2), actor);

        assertThat(response.durationWorkdays()).isEqualTo(3);
        assertThat(response.percentPlan()).isEqualByComparingTo("33.33");
    }

    private static Holiday holiday(LocalDate date) {
        Holiday holiday = new Holiday();
        holiday.setId(UUID.randomUUID());
        holiday.setHolidayDate(date);
        holiday.setName("Holiday");
        holiday.setType(com.prolean.timeline.domain.HolidayType.NATIONAL);
        holiday.setCreatedBy(UUID.randomUUID());
        return holiday;
    }
}
