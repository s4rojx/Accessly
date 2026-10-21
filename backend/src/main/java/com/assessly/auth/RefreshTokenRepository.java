package com.assessly.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    List<RefreshTokenEntity> findAllByTokenFamilyId(UUID tokenFamilyId);

    @Modifying
    @Query(value = "update refresh_tokens set revoked_at = :now where token_family_id = :familyId", nativeQuery = true)
    int revokeActiveFamily(@Param("familyId") UUID familyId, @Param("now") java.time.Instant now);
}
