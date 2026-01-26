package com.porcana.domain.guest;

import com.porcana.domain.guest.dto.GuestSessionResponse;
import com.porcana.domain.guest.service.GuestSessionService;
import com.porcana.global.guest.GuestSessionExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Guest Session", description = "게스트 세션 API")
@RestController
@RequestMapping("/api/v1/guest-sessions")
@RequiredArgsConstructor
public class GuestSessionController {

    private final GuestSessionService guestSessionService;

    @Operation(
            summary = "게스트 세션 생성",
            description = "비회원을 위한 게스트 세션을 생성하고 X-Guest-Session-Id 헤더로 반환합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "게스트 세션 생성 성공",
                            headers = @Header(
                                    name = "X-Guest-Session-Id",
                                    description = "생성된 게스트 세션 ID",
                                    schema = @Schema(type = "string", format = "uuid")
                            )
                    ),
                    @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<GuestSessionResponse> createGuestSession() {
        // Create new guest session
        UUID guestSessionId = guestSessionService.createGuestSession();

        // Set response header
        HttpHeaders headers = new HttpHeaders();
        headers.set(GuestSessionExtractor.GUEST_SESSION_HEADER, guestSessionId.toString());

        // Return response
        GuestSessionResponse guestSessionResponse = new GuestSessionResponse(guestSessionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(headers)
                .body(guestSessionResponse);
    }
}
