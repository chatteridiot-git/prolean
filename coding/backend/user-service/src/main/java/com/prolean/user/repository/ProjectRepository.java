package com.prolean.user.repository;

import com.prolean.user.domain.Project;
import com.prolean.user.domain.ProjectStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByCode(String code);

    Optional<Project> findByRequestId(UUID requestId);

    boolean existsByCode(String code);

    @Query("""
            select distinct p from Project p
            where (:admin = true or exists (
                     select 1 from ProjectMember pm where pm.project = p and pm.user.id = :userId))
              and (:status is null or p.status = :status)
              and (:q is null or lower(p.code) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.name) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Project> search(@Param("userId") UUID userId, @Param("admin") boolean admin,
            @Param("status") ProjectStatus status, @Param("q") String q, Pageable pageable);
}
