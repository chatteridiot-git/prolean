package com.prolean.task.repository;

import com.prolean.task.domain.Task;
import com.prolean.task.domain.TaskStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByRequestId(UUID requestId);

    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("""
            select t from Task t
            where t.projectId = :projectId
              and (:status is null or t.status = :status)
              and (:picUserId is null or t.picUserId = :picUserId)
              and (:q is null or lower(t.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(t.description) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Task> search(@Param("projectId") UUID projectId, @Param("status") TaskStatus status,
            @Param("picUserId") UUID picUserId, @Param("q") String q, Pageable pageable);
}
