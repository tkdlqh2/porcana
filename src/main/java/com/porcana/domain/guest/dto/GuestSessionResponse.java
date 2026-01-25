package com.porcana.domain.guest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 게스트 세션 생성 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GuestSessionResponse {
    /**
     * 게스트 세션 ID (디버깅용, 실제로는 쿠키로만 관리)
     */
    private UUID guestSessionId;
}
