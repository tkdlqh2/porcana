package com.porcana.domain.guest;

import com.porcana.domain.guest.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GuestSessionRepository extends JpaRepository<GuestSession, UUID> {

    /**
     * Find expired guest sessions for cleanup batch
     * @param expirationThreshold Sessions last seen before this timestamp are considered expired
     * @return List of expired guest sessions
     */
    @Query("SELECT gs FROM GuestSession gs WHERE gs.lastSeenAt < :expirationThreshold")
    List<GuestSession> findExpiredSessions(@Param("expirationThreshold") LocalDateTime expirationThreshold);

    /**
     * Delete expired guest sessions
     * @param expirationThreshold Sessions last seen before this timestamp will be deleted
     * @return Number of deleted sessions
     */
    @Modifying
    @Query("DELETE FROM GuestSession gs WHERE gs.lastSeenAt < :expirationThreshold")
    int deleteExpiredSessions(@Param("expirationThreshold") LocalDateTime expirationThreshold);
}
