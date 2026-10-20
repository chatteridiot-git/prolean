package com.prolean.auth.repository;

import com.prolean.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now, r.revokedReason = 'ROTATED', r.replacedBy = :replacementId
             where r.id = :id and r.revokedAt is null
            """)
    int markRotated(@Param("id") UUID id, @Param("now") Instant now,
            @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now, r.revokedReason = :reason
             where r.familyId = :familyId and r.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now,
            @Param("reason") String reason);

    @Modifying
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now, r.revokedReason = :reason
             where r.userId = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now,
            @Param("reason") String reason);
}
