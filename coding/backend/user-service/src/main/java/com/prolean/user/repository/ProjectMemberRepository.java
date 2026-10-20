package com.prolean.user.repository;

import com.prolean.user.domain.ProjectMember;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    @Query("select pm from ProjectMember pm where pm.project.id = :projectId and pm.user.id = :userId")
    Optional<ProjectMember> findMembership(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("select count(pm) > 0 from ProjectMember pm where pm.project.id = :projectId and pm.user.id = :userId")
    boolean existsMembership(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = "user")
    Page<ProjectMember> findByProject_Id(UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<ProjectMember> findByProject_Id(UUID projectId);

    long countByProject_Id(UUID projectId);

    @Query("select pm.user.id from ProjectMember pm where pm.project.id = :projectId")
    Set<UUID> findUserIdsByProjectId(@Param("projectId") UUID projectId);

    List<ProjectMember> findByProject_IdAndUser_IdIn(UUID projectId, Set<UUID> userIds);

    @Modifying
    @Query("delete from ProjectMember pm where pm.project.id = :projectId and pm.user.id = :userId")
    int deleteMembership(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
