package com.porcana.domain.auth.repository;

import com.porcana.domain.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(UUID token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prt from PasswordResetToken prt where prt.token = :token")
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") UUID token);

    void deleteAllByUserId(UUID userId);
}
