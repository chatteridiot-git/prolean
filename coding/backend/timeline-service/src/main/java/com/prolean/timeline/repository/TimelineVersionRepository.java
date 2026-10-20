package com.prolean.timeline.repository;

import com.prolean.timeline.domain.LifecycleStatus;
import com.prolean.timeline.domain.TimelineVersion;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimelineVersionRepository extends JpaRepository<TimelineVersion, UUID> {

    Optional<TimelineVersion> findByRequestId(UUID requestId);

    Optional<TimelineVersion> findByPublishRequestId(UUID publishRequestId);

    Optional<TimelineVersion> findByTimelineIdAndLifecycleStatus(UUID timelineId,
            LifecycleStatus lifecycleStatus);

    Optional<TimelineVersion> findFirstByTimelineIdOrderByVersionNumberDesc(UUID timelineId);

    Optional<TimelineVersion> findByIdAndTimelineId(UUID id, UUID timelineId);

    Page<TimelineVersion> findByTimelineId(UUID timelineId, Pageable pageable);

    long countByTimelineId(UUID timelineId);

    boolean existsByTimelineIdAndLifecycleStatusIn(UUID timelineId,
            Collection<LifecycleStatus> statuses);

    @Query("select coalesce(max(v.versionNumber), 0) from TimelineVersion v where v.timelineId = :timelineId")
    int maxVersionNumber(@Param("timelineId") UUID timelineId);
}
