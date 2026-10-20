package com.prolean.user.repository;

import com.prolean.user.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("""
            select distinct u from AppUser u
            where (:q is null or lower(u.username) like lower(concat('%', cast(:q as string), '%'))
                or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                or lower(u.fullName) like lower(concat('%', cast(:q as string), '%')))
              and (:enabled is null or u.enabled = :enabled)
              and (:role is null or exists (select 1 from u.roles r where r.name = cast(:role as string)))
            """)
    Page<AppUser> search(@Param("q") String q, @Param("enabled") Boolean enabled,
            @Param("role") String role, Pageable pageable);

    @Query("select count(distinct u) from AppUser u join u.roles r where r.name = 'ADMIN' and u.enabled = true")
    long countActiveAdmins();
}
