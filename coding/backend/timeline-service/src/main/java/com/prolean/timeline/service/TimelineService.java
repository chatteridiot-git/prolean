package com.prolean.timeline.service;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
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
import com.prolean.timeline.mapper.TimelineMapper;
import io.micrometer.core.instrument.Metrics;
import com.prolean.timeline.repository.HolidayRepository;
import com.prolean.timeline.repository.TimelineRepository;
import com.prolean.timeline.repository.TimelineVersionEventRepository;
import com.prolean.timeline.repository.TimelineVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TimelineService {

    private static final Set<String> SORT_FIELDS = Set.of(
            "versionNumber", "createdAt", "updatedAt", "lifecycleStatus");

    private final TimelineRepository timelineRepository;
    private final TimelineVersionRepository versionRepository;
    private final TimelineVersionEventRepository eventRepository;
    private final HolidayRepository holidayRepository;
    private final TimelineAccessClient accessClient;
    private final TimelineProperties properties;

    public TimelineService(TimelineRepository timelineRepository,
            TimelineVersionRepository versionRepository,
            TimelineVersionEventRepository eventRepository, HolidayRepository holidayRepository,
            TimelineAccessClient accessClient, TimelineProperties properties) {
        this.timelineRepository = timelineRepository;
        this.versionRepository = versionRepository;
        this.eventRepository = eventRepository;
        this.holidayRepository = holidayRepository;
        this.accessClient = accessClient;
        this.properties = properties;
    }

    public TimelineAggregateResponse aggregate(UUID projectId, CurrentUser actor) {
        requireAccess(projectId, actor, false);
        Timeline timeline = requireTimeline(projectId);
        TimelineVersion current = versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.PUBLISHED)
                .orElse(null);
        TimelineVersion draft = versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.DRAFT)
                .orElse(null);
        LocalDate today = properties.today();
        return TimelineMapper.aggregate(timeline.getId(), projectId,
                versionRepository.countByTimelineId(timeline.getId()),
                current == null ? null : toResponse(timeline, current, today),
                draft == null ? null : toResponse(timeline, draft, today));
    }

    public TimelineVersionResponse current(UUID projectId, LocalDate asOf, CurrentUser actor) {
        requireAccess(projectId, actor, false);
        Timeline timeline = requireTimeline(projectId);
        TimelineVersion current = versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_VERSION));
        return toResponse(timeline, current, asOf == null ? properties.today() : asOf);
    }

    public PageResponse<TimelineVersionResponse> versions(UUID projectId, PageQuery query,
            CurrentUser actor) {
        requireAccess(projectId, actor, false);
        Timeline timeline = requireTimeline(projectId);
        Pageable pageable = query.toPageable(SORT_FIELDS, "versionNumber,desc");
        Page<TimelineVersion> page = versionRepository.findByTimelineId(timeline.getId(), pageable);
        LocalDate today = properties.today();
        List<TimelineVersionResponse> content = page.getContent().stream()
                .map(version -> toResponse(timeline, version, today))
                .toList();
        return PageResponse.of(content, page);
    }

    public TimelineVersionResponse version(UUID projectId, UUID versionId, CurrentUser actor) {
        requireAccess(projectId, actor, false);
        Timeline timeline = requireTimeline(projectId);
        TimelineVersion version = versionRepository.findByIdAndTimelineId(versionId, timeline.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND,
                        "Timeline version not found."));
        return toResponse(timeline, version, properties.today());
    }

    @Transactional
    public VersionResult create(UUID projectId, CreateTimelineRequest request, CurrentUser actor) {
        requireAccess(projectId, actor, true);
        var replay = versionRepository.findByRequestId(request.requestId());
        if (replay.isPresent()) {
            TimelineVersion version = replay.get();
            Timeline timeline = timelineRepository.findById(version.getTimelineId())
                    .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND));
            if (!timeline.getProjectId().equals(projectId)) {
                throw new ApiException(ErrorCode.DATA_CONFLICT,
                        "The request id belongs to a timeline in another project.");
            }
            return new VersionResult(toResponse(timeline, version, properties.today()), true);
        }
        if (timelineRepository.findByProjectId(projectId).isPresent()) {
            throw new ApiException(ErrorCode.TIMELINE_EXISTS);
        }
        validateRange(request.startDate(), request.endDate(), true);

        Timeline timeline = new Timeline();
        timeline.setProjectId(projectId);
        timeline.setRequestId(request.requestId());
        timeline.setCreatedBy(actor.id());
        timeline = timelineRepository.save(timeline);

        TimelineVersion version = new TimelineVersion();
        version.setTimelineId(timeline.getId());
        version.setVersionNumber(1);
        version.setLifecycleStatus(LifecycleStatus.DRAFT);
        version.setStartDate(request.startDate());
        version.setEndDate(request.endDate());
        version.setOperationalStatus(OperationalStatus.OPEN);
        version.setPercentActual(0);
        version.setRevisionNotes(request.revisionNotes());
        version.setRequestId(request.requestId());
        version.setCreatedBy(actor.id());
        version.setUpdatedBy(actor.id());
        version = persist(version);
        record(version, TimelineEventType.CREATED, actor, Map.of("versionNumber", 1));
        requireAccess(projectId, actor, true);
        return new VersionResult(toResponse(timeline, version, properties.today()), false);
    }

    @Transactional
    public TimelineVersionResponse editDraft(UUID projectId, EditDraftRequest request,
            CurrentUser actor) {
        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion draft = requireDraft(timeline);
        checkDraftTimestamp(draft, request.expectedDraftUpdatedAt());
        validateRange(request.startDate(), request.endDate(), true);
        draft.setStartDate(request.startDate());
        draft.setEndDate(request.endDate());
        draft.setRevisionNotes(request.revisionNotes());
        draft.setUpdatedBy(actor.id());
        draft = persist(draft);
        record(draft, TimelineEventType.UPDATED, actor,
                Map.of("startDate", request.startDate().toString(),
                        "endDate", request.endDate().toString()));
        requireAccess(projectId, actor, true);
        return toResponse(timeline, draft, properties.today());
    }

    @Transactional
    public void cancelDraft(UUID projectId, Instant expectedDraftUpdatedAt, CurrentUser actor) {
        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion draft = requireDraft(timeline);
        checkDraftTimestamp(draft, expectedDraftUpdatedAt);

        boolean everPublished = versionRepository.existsByTimelineIdAndLifecycleStatusIn(
                timeline.getId(), List.of(LifecycleStatus.PUBLISHED, LifecycleStatus.SUPERSEDED));
        long versionCount = versionRepository.countByTimelineId(timeline.getId());
        if (!everPublished && versionCount == 1) {
            requireAccess(projectId, actor, true);
            timelineRepository.delete(timeline);
            return;
        }

        LocalDate today = properties.today();
        WorkingDayCalculator.PlanResult plan = calculatePlan(draft, today);
        draft.setLifecycleStatus(LifecycleStatus.CANCELLED);
        draft.setCancelledBy(actor.id());
        draft.setCancelledAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        draft.setUpdatedBy(actor.id());
        draft.setSnapshotDurationWorkdays(plan.durationWorkdays());
        draft.setSnapshotPercentPlan(plan.percentPlan());
        draft.setSnapshotPercentActual(0);
        draft.setSnapshotAsOfDate(today);
        requireAccess(projectId, actor, true);
        persist(draft);
        record(draft, TimelineEventType.CANCELLED, actor, Map.of("versionNumber", draft.getVersionNumber()));
        requireAccess(projectId, actor, true);
    }

    @Transactional
    public VersionResult publish(UUID projectId, PublishRequest request, CurrentUser actor) {
        requireAccess(projectId, actor, true);
        var replay = versionRepository.findByPublishRequestId(request.requestId());
        if (replay.isPresent()) {
            TimelineVersion version = replay.get();
            Timeline timeline = timelineRepository.findById(version.getTimelineId())
                    .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND));
            if (!timeline.getProjectId().equals(projectId)) {
                throw new ApiException(ErrorCode.DATA_CONFLICT,
                        "The publish request id belongs to another project.");
            }
            return new VersionResult(toResponse(timeline, version, properties.today()), true);
        }

        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion draft = requireDraft(timeline);
        checkDraftTimestamp(draft, request.expectedDraftUpdatedAt());
        TimelineVersion current = versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.PUBLISHED)
                .orElse(null);
        checkCurrentTimestamp(current, request.expectedCurrentUpdatedAt());

        OperationalStatus carriedStatus = current == null
                ? OperationalStatus.OPEN
                : current.getOperationalStatus();
        int carriedActual = current == null ? 0 : current.getPercentActual();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (current != null) {
            WorkingDayCalculator.PlanResult snapshot = calculatePlan(current, properties.today());
            current.setLifecycleStatus(LifecycleStatus.SUPERSEDED);
            current.setSupersededBy(actor.id());
            current.setSupersededAt(now);
            current.setUpdatedBy(actor.id());
            current.setSnapshotDurationWorkdays(snapshot.durationWorkdays());
            current.setSnapshotPercentPlan(snapshot.percentPlan());
            current.setSnapshotPercentActual(current.getPercentActual());
            current.setSnapshotAsOfDate(snapshot.asOfDate());
            persist(current);
            record(current, TimelineEventType.SUPERSEDED, actor,
                    Map.of("supersededByVersion", draft.getVersionNumber()));
        }

        draft.setLifecycleStatus(LifecycleStatus.PUBLISHED);
        draft.setOperationalStatus(carriedStatus);
        draft.setPercentActual(carriedActual);
        draft.setPublishedBy(actor.id());
        draft.setPublishedAt(now);
        draft.setUpdatedBy(actor.id());
        draft.setPublishRequestId(request.requestId());
        draft = persist(draft);
        record(draft, TimelineEventType.PUBLISHED, actor,
                Map.of("versionNumber", draft.getVersionNumber()));
        requireAccess(projectId, actor, true);
        return new VersionResult(toResponse(timeline, draft, properties.today()), false);
    }

    @Transactional
    public VersionResult createRevision(UUID projectId, CreateRevisionRequest request,
            CurrentUser actor) {
        requireAccess(projectId, actor, true);
        var replay = versionRepository.findByRequestId(request.requestId());
        if (replay.isPresent()) {
            TimelineVersion version = replay.get();
            Timeline timeline = timelineRepository.findById(version.getTimelineId())
                    .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND));
            if (!timeline.getProjectId().equals(projectId)) {
                throw new ApiException(ErrorCode.DATA_CONFLICT,
                        "The request id belongs to a timeline in another project.");
            }
            return new VersionResult(toResponse(timeline, version, properties.today()), true);
        }

        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion current = versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_VERSION));
        if (versionRepository.findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.DRAFT)
                .isPresent()) {
            throw new ApiException(ErrorCode.DRAFT_EXISTS);
        }
        LocalDate start = request.startDate() == null ? current.getStartDate() : request.startDate();
        LocalDate end = request.endDate() == null ? current.getEndDate() : request.endDate();
        validateRange(start, end, true);

        TimelineVersion draft = new TimelineVersion();
        draft.setTimelineId(timeline.getId());
        draft.setVersionNumber(versionRepository.maxVersionNumber(timeline.getId()) + 1);
        draft.setLifecycleStatus(LifecycleStatus.DRAFT);
        draft.setStartDate(start);
        draft.setEndDate(end);
        draft.setOperationalStatus(OperationalStatus.OPEN);
        draft.setPercentActual(0);
        draft.setRevisionNotes(request.revisionNotes() == null
                ? current.getRevisionNotes()
                : request.revisionNotes());
        draft.setRequestId(request.requestId());
        draft.setCreatedBy(actor.id());
        draft.setUpdatedBy(actor.id());
        draft = persist(draft);
        record(draft, TimelineEventType.CREATED, actor,
                Map.of("versionNumber", draft.getVersionNumber()));
        requireAccess(projectId, actor, true);
        return new VersionResult(toResponse(timeline, draft, properties.today()), false);
    }

    @Transactional
    public TimelineVersionResponse changeStatus(UUID projectId, OperationalStatus target,
            CurrentUser actor) {
        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion current = requireCurrent(timeline);
        OperationalStatus previous = current.getOperationalStatus();
        validateTransition(previous, target);
        current.setOperationalStatus(target);
        if (target == OperationalStatus.CLOSED) {
            current.setPercentActual(100);
        }
        current.setUpdatedBy(actor.id());
        requireAccess(projectId, actor, true);
        current = persist(current);
        record(current, TimelineEventType.STATUS_CHANGED, actor,
                Map.of("previous", previous.name(), "current", target.name()));
        return toResponse(timeline, current, properties.today());
    }

    @Transactional
    public TimelineVersionResponse changeProgress(UUID projectId, int percentActual,
            CurrentUser actor) {
        Timeline timeline = lockTimeline(projectId);
        requireAccess(projectId, actor, true);
        TimelineVersion current = requireCurrent(timeline);
        if (current.getOperationalStatus() == OperationalStatus.CLOSED) {
            throw new ApiException(ErrorCode.TIMELINE_CLOSED);
        }
        int previous = current.getPercentActual();
        current.setPercentActual(percentActual);
        current.setUpdatedBy(actor.id());
        requireAccess(projectId, actor, true);
        current = persist(current);
        record(current, TimelineEventType.PROGRESS_CHANGED, actor,
                Map.of("previous", previous, "current", percentActual));
        return toResponse(timeline, current, properties.today());
    }

    /**
     * Saves a version and flushes immediately so entity callbacks (and therefore the
     * updated_at value returned to clients) match the row already stored in PostgreSQL.
     */
    private TimelineVersion persist(TimelineVersion version) {
        TimelineVersion saved = versionRepository.save(version);
        versionRepository.flush();
        return saved;
    }

    private Timeline lockTimeline(UUID projectId) {
        return timelineRepository.findByProjectIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND));
    }

    private Timeline requireTimeline(UUID projectId) {
        return timelineRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMELINE_NOT_FOUND));
    }

    private TimelineVersion requireDraft(Timeline timeline) {
        return versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.DRAFT)
                .orElseThrow(() -> new ApiException(ErrorCode.DRAFT_NOT_FOUND));
    }

    private TimelineVersion requireCurrent(Timeline timeline) {
        return versionRepository
                .findByTimelineIdAndLifecycleStatus(timeline.getId(), LifecycleStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_VERSION));
    }

    private void requireAccess(UUID projectId, CurrentUser actor, boolean write) {
        TimelineAccessClient.Access access = accessClient.access(projectId, actor.id());
        if (access == null || !access.allowed()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not a member of this project.");
        }
        if (write && !"ACTIVE".equals(access.projectStatus())) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED);
        }
    }

    private void checkDraftTimestamp(TimelineVersion draft, Instant expected) {
        if (expected != null && !sameInstant(expected, draft.getUpdatedAt())) {
            Metrics.counter("prolean.timeline.version.conflict", "code",
                    ErrorCode.DRAFT_MODIFIED.name()).increment();
            throw new ApiException(ErrorCode.DRAFT_MODIFIED);
        }
    }

    private void checkCurrentTimestamp(TimelineVersion current, Instant expected) {
        if (current == null) {
            if (expected != null) {
                throw new ApiException(ErrorCode.CURRENT_VERSION_CHANGED);
            }
            return;
        }
        if (expected == null || !sameInstant(expected, current.getUpdatedAt())) {
            Metrics.counter("prolean.timeline.version.conflict", "code",
                    ErrorCode.CURRENT_VERSION_CHANGED.name()).increment();
            throw new ApiException(ErrorCode.CURRENT_VERSION_CHANGED);
        }
    }

    private boolean sameInstant(Instant expected, Instant actual) {
        return expected != null && actual != null
                && expected.truncatedTo(ChronoUnit.MICROS)
                        .equals(actual.truncatedTo(ChronoUnit.MICROS));
    }

    private void validateRange(LocalDate startDate, LocalDate endDate, boolean requireWorkingDays) {
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        HolidayService.Horizon horizon = HolidayService.calendarHorizon(
                holidayRepository.findMinimumHolidayDate(),
                holidayRepository.findMaximumHolidayDate());
        if (horizon.startDate() == null || horizon.endDate() == null
                || startDate.isBefore(horizon.startDate()) || endDate.isAfter(horizon.endDate())) {
            throw new ApiException(ErrorCode.HOLIDAY_COVERAGE_REQUIRED);
        }
        if (requireWorkingDays) {
            Set<LocalDate> holidays = holidaysBetween(startDate, endDate);
            if (WorkingDayCalculator.countWorkingDays(startDate, endDate, holidays) == 0) {
                throw new ApiException(ErrorCode.NO_WORKING_DAYS);
            }
        }
    }

    private void validateTransition(OperationalStatus current, OperationalStatus target) {
        if (current == target) {
            return;
        }
        boolean allowed = switch (current) {
            case OPEN -> target == OperationalStatus.IN_PROGRESS || target == OperationalStatus.CLOSED;
            case IN_PROGRESS -> target == OperationalStatus.CLOSED;
            case CLOSED -> target == OperationalStatus.IN_PROGRESS;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Transition " + current + " -> " + target + " is not allowed.");
        }
    }

    private TimelineVersionResponse toResponse(Timeline timeline, TimelineVersion version, LocalDate asOf) {
        return TimelineMapper.toResponse(version, timeline.getProjectId(), planFor(version, asOf));
    }

    private WorkingDayCalculator.PlanResult planFor(TimelineVersion version, LocalDate asOf) {
        if (version.isFrozen() && version.getSnapshotDurationWorkdays() != null
                && version.getSnapshotPercentPlan() != null) {
            return new WorkingDayCalculator.PlanResult(
                    version.getSnapshotDurationWorkdays(),
                    version.getSnapshotPercentPlan(),
                    version.getSnapshotAsOfDate(),
                    version.getSnapshotDurationWorkdays() == 0 ? WorkingDayCalculator.NO_WORKING_DAYS : null);
        }
        return calculatePlan(version, asOf);
    }

    private WorkingDayCalculator.PlanResult calculatePlan(TimelineVersion version, LocalDate asOf) {
        return WorkingDayCalculator.calculatePlan(version.getStartDate(), version.getEndDate(), asOf,
                holidaysBetween(version.getStartDate(), version.getEndDate()));
    }

    private Set<LocalDate> holidaysBetween(LocalDate startDate, LocalDate endDate) {
        List<Holiday> holidays = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(startDate, endDate);
        Set<LocalDate> dates = new HashSet<>(holidays.size());
        holidays.forEach(holiday -> dates.add(holiday.getHolidayDate()));
        return dates;
    }

    private void record(TimelineVersion version, TimelineEventType type, CurrentUser actor,
            Map<String, Object> details) {
        TimelineVersionEvent event = new TimelineVersionEvent();
        event.setTimelineVersionId(version.getId());
        event.setEventType(type);
        event.setActorId(actor.id());
        event.setDetails(new LinkedHashMap<>(details));
        eventRepository.save(event);
    }
}
