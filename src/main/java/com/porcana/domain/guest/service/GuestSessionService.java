package com.porcana.domain.guest.service;

import com.porcana.domain.guest.GuestSessionRepository;
import com.porcana.domain.guest.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 게스트 세션 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestSessionService {

    private final GuestSessionRepository guestSessionRepository;

    /**
     * 새로운 게스트 세션 생성
     * @return 생성된 게스트 세션 ID
     */
    @Transactional
    public UUID createGuestSession() {
        GuestSession guestSession = GuestSession.create();
        GuestSession saved = guestSessionRepository.save(guestSession);
        log.info("Created guest session: {}", saved.getId());
        return saved.getId();
    }

    /**
     * 게스트 세션의 마지막 활동 시각 업데이트
     * @param guestSessionId 게스트 세션 ID
     */
    @Transactional
    public void updateLastSeenAt(UUID guestSessionId) {
        guestSessionRepository.findById(guestSessionId).ifPresent(session -> {
            session.updateLastSeenAt();
            log.debug("Updated last_seen_at for guest session: {}", guestSessionId);
        });
    }

    /**
     * 게스트 세션 존재 여부 확인
     * @param guestSessionId 게스트 세션 ID
     * @return 존재 여부
     */
    @Transactional(readOnly = true)
    public boolean exists(UUID guestSessionId) {
        return guestSessionRepository.existsById(guestSessionId);
    }
}
