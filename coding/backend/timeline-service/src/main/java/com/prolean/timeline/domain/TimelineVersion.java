package com.prolean.timeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "timeline_version")
public class TimelineVersion {

    @Id
    private UUID id;

    @Column(name = "timeline_id", nullable = false)
    private UUID timelineId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private LifecycleStatus lifecycleStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    private OperationalStatus operationalStatus = OperationalStatus.OPEN;

    @Column(name = "percent_actual", nullable = false)
    private int percentActual;

    @Column(name = "revision_notes", length = 1000)
    private String revisionNotes;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "publish_request_id", unique = true)
    private UUID publishRequestId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "snapshot_duration_workdays")
    private Integer snapshotDurationWorkdays;

    @Column(name = "snapshot_percent_plan", precision = 5, scale = 2)
    private BigDecimal snapshotPercentPlan;

    @Column(name = "snapshot_percent_actual")
    private Integer snapshotPercentActual;

    @Column(name = "snapshot_as_of_date")
    private LocalDate snapshotAsOfDate;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isFrozen() {
        return lifecycleStatus == LifecycleStatus.SUPERSEDED
                || lifecycleStatus == LifecycleStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTimelineId() {
        return timelineId;
    }

    public void setTimelineId(UUID timelineId) {
        this.timelineId = timelineId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(LifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public OperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(OperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public int getPercentActual() {
        return percentActual;
    }

    public void setPercentActual(int percentActual) {
        this.percentActual = percentActual;
    }

    public String getRevisionNotes() {
        return revisionNotes;
    }

    public void setRevisionNotes(String revisionNotes) {
        this.revisionNotes = revisionNotes;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getPublishRequestId() {
        return publishRequestId;
    }

    public void setPublishRequestId(UUID publishRequestId) {
        this.publishRequestId = publishRequestId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UUID publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(UUID supersededBy) {
        this.supersededBy = supersededBy;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void setSupersededAt(Instant supersededAt) {
        this.supersededAt = supersededAt;
    }

    public Integer getSnapshotDurationWorkdays() {
        return snapshotDurationWorkdays;
    }

    public void setSnapshotDurationWorkdays(Integer snapshotDurationWorkdays) {
        this.snapshotDurationWorkdays = snapshotDurationWorkdays;
    }

    public BigDecimal getSnapshotPercentPlan() {
        return snapshotPercentPlan;
    }

    public void setSnapshotPercentPlan(BigDecimal snapshotPercentPlan) {
        this.snapshotPercentPlan = snapshotPercentPlan;
    }

    public Integer getSnapshotPercentActual() {
        return snapshotPercentActual;
    }

    public void setSnapshotPercentActual(Integer snapshotPercentActual) {
        this.snapshotPercentActual = snapshotPercentActual;
    }

    public LocalDate getSnapshotAsOfDate() {
        return snapshotAsOfDate;
    }

    public void setSnapshotAsOfDate(LocalDate snapshotAsOfDate) {
        this.snapshotAsOfDate = snapshotAsOfDate;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(UUID cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
