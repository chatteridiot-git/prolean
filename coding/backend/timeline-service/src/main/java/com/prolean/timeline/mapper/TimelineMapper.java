package com.prolean.timeline.mapper;

import com.prolean.timeline.domain.TimelineVersion;
import com.prolean.timeline.dto.TimelineDtos.TimelineAggregateResponse;
import com.prolean.timeline.dto.TimelineDtos.TimelineVersionResponse;
import com.prolean.timeline.service.WorkingDayCalculator.PlanResult;
import java.math.BigDecimal;
import java.util.UUID;

public final class TimelineMapper {

    private TimelineMapper() {
    }

    public static TimelineVersionResponse toResponse(TimelineVersion version, UUID projectId,
            PlanResult livePlan) {
        boolean frozen = version.isFrozen();
        int duration = frozen && version.getSnapshotDurationWorkdays() != null
                ? version.getSnapshotDurationWorkdays()
                : livePlan.durationWorkdays();
        BigDecimal percentPlan = frozen && version.getSnapshotPercentPlan() != null
                ? version.getSnapshotPercentPlan()
                : livePlan.percentPlan();
        java.time.LocalDate asOf = frozen && version.getSnapshotAsOfDate() != null
                ? version.getSnapshotAsOfDate()
                : livePlan.asOfDate();
        String warning = duration == 0 ? "NO_WORKING_DAYS" : null;
        int percentActual = frozen && version.getSnapshotPercentActual() != null
                ? version.getSnapshotPercentActual()
                : version.getPercentActual();

        return new TimelineVersionResponse(
                version.getId(),
                version.getTimelineId(),
                projectId,
                version.getVersionNumber(),
                version.getLifecycleStatus(),
                version.getStartDate(),
                version.getEndDate(),
                duration,
                version.getOperationalStatus(),
                percentActual,
                percentPlan.setScale(2),
                asOf,
                warning,
                version.getRevisionNotes(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getUpdatedBy(),
                version.getUpdatedAt(),
                version.getPublishedBy(),
                version.getPublishedAt(),
                version.getSupersededBy(),
                version.getSupersededAt(),
                version.getSnapshotDurationWorkdays(),
                version.getSnapshotPercentPlan(),
                version.getSnapshotPercentActual(),
                version.getSnapshotAsOfDate(),
                version.getCancelledBy(),
                version.getCancelledAt());
    }

    public static TimelineAggregateResponse aggregate(UUID timelineId, UUID projectId, long versionCount,
            TimelineVersionResponse current, TimelineVersionResponse draft) {
        return new TimelineAggregateResponse(timelineId, projectId, versionCount, current, draft);
    }
}
