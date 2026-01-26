package com.porcana.domain.arena;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.arena.dto.CreateSessionRequest;
import com.porcana.domain.arena.dto.PickAssetRequest;
import com.porcana.domain.arena.dto.PickPreferencesRequest;
import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.guest.service.GuestSessionService;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Sql(scripts = "/sql/arena-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ArenaControllerTest extends BaseIntegrationTest {

    @Autowired
    private ArenaSessionRepository arenaSessionRepository;

    @Autowired
    private ArenaRoundRepository arenaRoundRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private GuestSessionService guestSessionService;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_PORTFOLIO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private String accessToken;

    @BeforeEach
    void setUp() {
        // Clean up arena data
        arenaRoundRepository.deleteAll();
        arenaSessionRepository.deleteAll();

        // Generate JWT token for test user
        accessToken = jwtTokenProvider.createAccessToken(TEST_USER_ID);
    }

    @Test
    @DisplayName("아레나 세션 생성 성공")
    void createSession_success() {
        CreateSessionRequest request = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(request)
                .log().all()
        .when()
                .post("/arena/sessions")
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", notNullValue())
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(0));
    }

    @Test
    @DisplayName("아레나 세션 생성 실패 - 인증 토큰 없음")
    void createSession_fail_noAuth() {
        CreateSessionRequest request = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("아레나 세션 생성 실패 - 유효하지 않은 포트폴리오 ID")
    void createSession_fail_invalidPortfolioId() {
        UUID invalidPortfolioId = UUID.randomUUID();
        CreateSessionRequest request = new CreateSessionRequest(invalidPortfolioId);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(request)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("아레나 세션 조회 성공")
    void getSession_success() {
        // Create session first
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Get session
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}", sessionId)
        .then()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(0))
                .body("totalRounds", equalTo(11));
    }

    @Test
    @DisplayName("아레나 세션 조회 실패 - 존재하지 않는 세션")
    void getSession_fail_notFound() {
        UUID invalidSessionId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}", invalidSessionId)
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("현재 라운드 조회 성공 - Round 0 (Pre Round)")
    void getCurrentRound_success_round0() {
        // Create session
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Get current round (should be round 0)
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .log().all()
                .body("sessionId", equalTo(sessionId))
                .body("round", equalTo(0))
                .body("roundType", equalTo("PRE_ROUND"))
                .body("riskProfileOptions", hasSize(3))
                .body("riskProfileOptions[0].value", notNullValue())
                .body("riskProfileOptions[0].displayName", notNullValue())
                .body("riskProfileOptions[0].description", notNullValue())
                .body("sectorOptions", notNullValue())
                .body("minSectorSelection", equalTo(0))
                .body("maxSectorSelection", equalTo(3));
    }

    @Test
    @DisplayName("Pre Round 선택 성공 - Risk Profile + Sectors")
    void pickPreferences_success() {
        // Create session
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Pick risk profile and sectors
        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickPreferencesRequest pickRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
                .log().all()
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(1))
                .body("picked.riskProfile", equalTo("BALANCED"))
                .body("picked.sectors", hasSize(2));
    }

    @Test
    @DisplayName("Pre Round 선택 실패 - validation 오류")
    void pickPreferences_fail_validation() {
        // Create session
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Pick with null risk profile
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body("{\"riskProfile\": null, \"sectors\": []}")
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Pre Round 섹터 선택 실패 - 4개 선택 (최대 3개)")
    void pickPreferences_fail_tooManySectors() {
        // Create session
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE,
                Sector.FINANCIALS,
                Sector.ENERGY
        );
        PickPreferencesRequest pickRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("자산 선택 성공")
    void pickAsset_success() {
        // Create session and pick preferences
        String sessionId = createSessionAndPickPreferences();

        // Get current round to see asset options
        String assetId = given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .body("roundType", equalTo("ASSET"))
                .body("assets", hasSize(3))
                .extract()
                .path("assets[0].assetId");

        // Pick an asset
        PickAssetRequest pickRequest = new PickAssetRequest(UUID.fromString(assetId));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
                .log().all()
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-asset", sessionId)
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(2))
                .body("picked", equalTo(assetId));
    }

    @Test
    @DisplayName("자산 선택 실패 - 유효하지 않은 자산 ID")
    void pickAsset_fail_invalidAssetId() {
        String sessionId = createSessionAndPickPreferences();

        UUID invalidAssetId = UUID.randomUUID();
        PickAssetRequest pickRequest = new PickAssetRequest(invalidAssetId);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-asset", sessionId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("전체 아레나 플로우 테스트")
    void fullArenaFlow_success() {
        // 1. Create session
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);
        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(0))
                .extract()
                .path("sessionId");

        // 2. Pick preferences (Round 0 - Risk Profile + Sectors)
        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickPreferencesRequest preferencesRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(preferencesRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(1));

        // 3. Pick assets (Rounds 1-10)
        for (int round = 1; round <= 10; round++) {
            // Get current round options
            String assetId = given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
            .then()
                    .statusCode(200)
                    .body("round", equalTo(round))
                    .body("roundType", equalTo("ASSET"))
                    .body("assets", hasSize(3))
                    .extract()
                    .path("assets[0].assetId");

            // Pick first asset
            PickAssetRequest assetRequest = new PickAssetRequest(UUID.fromString(assetId));
            int expectedNextRound = round < 10 ? round + 1 : 10;
            String expectedStatus = round < 10 ? "IN_PROGRESS" : "COMPLETED";

            given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(assetRequest)
            .when()
                    .post("/arena/sessions/{sessionId}/rounds/current/pick-asset", sessionId)
            .then()
                    .statusCode(200)
                    .body("currentRound", equalTo(expectedNextRound))
                    .body("status", equalTo(expectedStatus));
        }

        // 4. Verify session is completed
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}", sessionId)
        .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("currentRound", equalTo(10))
                .body("selectedAssetIds", hasSize(10));
    }

    // ========== Guest Session Tests ==========

    @Test
    @DisplayName("게스트 세션 - 아레나 세션 생성 성공")
    void createSession_success_guest() {
        // Create guest session directly
        UUID guestSessionId = guestSessionService.createGuestSession();

        // Create guest portfolio
        String guestPortfolioId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body("{\"name\": \"Guest Test Portfolio\"}")
                .log().all()
        .when()
                .post("/portfolios")
        .then()
                .log().all()
                .statusCode(200)
                .extract()
                .path("portfolioId");

        // Create arena session
        CreateSessionRequest request = new CreateSessionRequest(UUID.fromString(guestPortfolioId));

        given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(request)
                .log().all()
        .when()
                .post("/arena/sessions")
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", notNullValue())
                .body("portfolioId", equalTo(guestPortfolioId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(0));
    }

    @Test
    @DisplayName("게스트 세션 - 전체 아레나 플로우 테스트")
    void fullArenaFlow_success_guest() {
        // 1. Create guest session directly
        UUID guestSessionId = guestSessionService.createGuestSession();

        // 2. Create guest portfolio
        String guestPortfolioId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body("{\"name\": \"Guest Arena Portfolio\"}")
        .when()
                .post("/portfolios")
        .then()
                .statusCode(200)
                .extract()
                .path("portfolioId");

        // 3. Create arena session
        CreateSessionRequest createRequest = new CreateSessionRequest(UUID.fromString(guestPortfolioId));
        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(0))
                .extract()
                .path("sessionId");

        // 4. Get current round (Round 0 - Pre Round)
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("round", equalTo(0))
                .body("roundType", equalTo("PRE_ROUND"))
                .body("riskProfileOptions", hasSize(3))
                .body("sectorOptions", notNullValue());

        // 5. Pick preferences (Round 0 - Risk Profile + Sectors)
        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickPreferencesRequest preferencesRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);
        given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(preferencesRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(1))
                .body("picked.riskProfile", equalTo("BALANCED"));

        // 6. Pick assets (Rounds 1-10)
        for (int round = 1; round <= 10; round++) {
            // Get current round options
            String assetId = given()
                    .header("X-Guest-Session-Id", guestSessionId.toString())
            .when()
                    .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
            .then()
                    .statusCode(200)
                    .body("round", equalTo(round))
                    .body("roundType", equalTo("ASSET"))
                    .body("assets", hasSize(3))
                    .extract()
                    .path("assets[0].assetId");

            // Pick first asset
            PickAssetRequest assetRequest = new PickAssetRequest(UUID.fromString(assetId));
            int expectedNextRound = round < 10 ? round + 1 : 10;
            String expectedStatus = round < 10 ? "IN_PROGRESS" : "COMPLETED";

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Guest-Session-Id", guestSessionId.toString())
                    .body(assetRequest)
            .when()
                    .post("/arena/sessions/{sessionId}/rounds/current/pick-asset", sessionId)
            .then()
                    .statusCode(200)
                    .body("currentRound", equalTo(expectedNextRound))
                    .body("status", equalTo(expectedStatus));
        }

        // 7. Verify session is completed
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/arena/sessions/{sessionId}", sessionId)
        .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("currentRound", equalTo(10))
                .body("selectedAssetIds", hasSize(10));

        // 8. Verify portfolio is now ACTIVE with 10 assets
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/portfolios/{portfolioId}", guestPortfolioId)
        .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"))
                .body("positions", hasSize(10));
    }

    @Test
    @DisplayName("게스트 세션 - 라운드 조회 성공")
    void getCurrentRound_success_guest() {
        // Create guest session directly
        UUID guestSessionId = guestSessionService.createGuestSession();

        String guestPortfolioId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body("{\"name\": \"Guest Portfolio\"}")
        .when()
                .post("/portfolios")
        .then()
                .statusCode(200)
                .extract()
                .path("portfolioId");

        // Create arena session
        CreateSessionRequest createRequest = new CreateSessionRequest(UUID.fromString(guestPortfolioId));
        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Get current round
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("round", equalTo(0))
                .body("roundType", equalTo("PRE_ROUND"));
    }

    @Test
    @DisplayName("게스트 세션 - 자산 선택 성공")
    void pickAsset_success_guest() {
        // Create guest session directly
        UUID guestSessionId = guestSessionService.createGuestSession();

        String guestPortfolioId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body("{\"name\": \"Guest Portfolio\"}")
        .when()
                .post("/portfolios")
        .then()
                .statusCode(200)
                .extract()
                .path("portfolioId");

        CreateSessionRequest createRequest = new CreateSessionRequest(UUID.fromString(guestPortfolioId));
        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .log().all()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Pick preferences first
        List<Sector> sectors = Arrays.asList(Sector.INFORMATION_TECHNOLOGY, Sector.HEALTH_CARE);
        PickPreferencesRequest preferencesRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);
        given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(preferencesRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(200);

        // Get asset options and pick one
        String assetId = given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .body("roundType", equalTo("ASSET"))
                .body("assets", hasSize(3))
                .extract()
                .path("assets[0].assetId");

        PickAssetRequest pickRequest = new PickAssetRequest(UUID.fromString(assetId));
        given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", guestSessionId.toString())
                .body(pickRequest)
                .log().all()
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-asset", sessionId)
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(2))
                .body("picked", equalTo(assetId));
    }

    // Helper methods

    private String createSessionAndPickPreferences() {
        CreateSessionRequest createRequest = new CreateSessionRequest(TEST_PORTFOLIO_ID);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(createRequest)
        .when()
                .post("/arena/sessions")
        .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickPreferencesRequest pickRequest = new PickPreferencesRequest(RiskProfile.BALANCED, sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-preferences", sessionId)
        .then()
                .statusCode(200);

        return sessionId;
    }
}