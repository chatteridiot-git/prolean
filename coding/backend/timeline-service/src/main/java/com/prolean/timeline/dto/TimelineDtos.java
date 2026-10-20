package com.prolean.timeline.dto;

import com.prolean.timeline.domain.LifecycleStatus;
import com.prolean.timeline.domain.OperationalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TimelineDtos {

    private TimelineDtos() {
    }

    public record CreateTimelineRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull UUID requestId,
            @Size(max = 1000) String revisionNotes) {
    }

    public record EditDraftRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 1000) String revisionNotes,
            Instant expectedDraftUpdatedAt) {
    }

    public record PublishRequest(
            @NotNull UUID requestId,
            Instant expectedDraftUpdatedAt,
            Instant expectedCurrentUpdatedAt) {
    }

    public record CreateRevisionRequest(
            LocalDate startDate,
            LocalDate endDate,
            @NotNull UUID requestId,
            @Size(max = 1000) String revisionNotes) {
    }

    public record StatusRequest(@NotNull OperationalStatus status) {
    }

    public record ProgressRequest(@NotNull @Min(0) @Max(100) Integer percentActual) {
    }

    public record TimelineVersionResponse(
            UUID id,
            UUID timelineId,
            UUID projectId,
            int versionNumber,
            LifecycleStatus lifecycleStatus,
            LocalDate startDate,
            LocalDate endDate,
            int durationWorkdays,
            OperationalStatus operationalStatus,
            int percentActual,
            BigDecimal percentPlan,
            LocalDate planAsOfDate,
            String planWarning,
            String revisionNotes,
            UUID createdBy,
            Instant createdAt,
            UUID updatedBy,
            Instant updatedAt,
            UUID publishedBy,
            Instant publishedAt,
            UUID supersededBy,
            Instant supersededAt,
            Integer snapshotDurationWorkdays,
            BigDecimal snapshotPercentPlan,
            Integer snapshotPercentActual,
            LocalDate snapshotAsOfDate,
            UUID cancelledBy,
            Instant cancelledAt) {
    }

    public record TimelineAggregateResponse(
            UUID timelineId,
            UUID projectId,
            long versionCount,
            TimelineVersionResponse current,
            TimelineVersionResponse draft) {
    }

    public record VersionResult(TimelineVersionResponse version, boolean replay) {
    }
}
