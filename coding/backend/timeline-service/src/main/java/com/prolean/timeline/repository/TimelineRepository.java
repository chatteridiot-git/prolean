package com.prolean.timeline.repository;

import com.prolean.timeline.domain.Timeline;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimelineRepository extends JpaRepository<Timeline, UUID> {

    Optional<Timeline> findByProjectId(UUID projectId);

    Optional<Timeline> findByRequestId(UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Timeline t where t.projectId = :projectId")
    Optional<Timeline> findByProjectIdForUpdate(@Param("projectId") UUID projectId);
}
