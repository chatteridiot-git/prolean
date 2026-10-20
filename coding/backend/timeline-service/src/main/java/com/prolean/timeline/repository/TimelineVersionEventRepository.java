package com.prolean.timeline.repository;

import com.prolean.timeline.domain.TimelineVersionEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineVersionEventRepository extends JpaRepository<TimelineVersionEvent, UUID> {

    List<TimelineVersionEvent> findByTimelineVersionIdOrderByOccurredAtAsc(UUID timelineVersionId);
}
