package com.porcana.domain.arena;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.arena.dto.CreateSessionRequest;
import com.porcana.domain.arena.dto.PickAssetRequest;
import com.porcana.domain.arena.dto.PickRiskProfileRequest;
import com.porcana.domain.arena.dto.PickSectorsRequest;
import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.repository.ArenaRoundRepository;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.asset.entity.Sector;
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
                .body("currentRound", equalTo(1));
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
                .statusCode(401);
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
                .body("currentRound", equalTo(1))
                .body("totalRounds", equalTo(12));
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
    @DisplayName("현재 라운드 조회 성공 - Round 1 (Risk Profile)")
    void getCurrentRound_success_round1() {
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

        // Get current round (should be round 1)
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}/rounds/current", sessionId)
        .then()
                .statusCode(200)
                .log().all()
                .body("sessionId", equalTo(sessionId))
                .body("round", equalTo(1))
                .body("roundType", equalTo("RISK_PROFILE"))
                .body("options", hasSize(3))
                .body("options[0].value", notNullValue())
                .body("options[0].displayName", notNullValue())
                .body("options[0].description", notNullValue());
    }

    @Test
    @DisplayName("리스크 프로필 선택 성공")
    void pickRiskProfile_success() {
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

        // Pick risk profile
        PickRiskProfileRequest pickRequest = new PickRiskProfileRequest(RiskProfile.BALANCED);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
                .log().all()
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-risk-profile", sessionId)
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(2))
                .body("picked", equalTo("BALANCED"));
    }

    @Test
    @DisplayName("리스크 프로필 선택 실패 - validation 오류")
    void pickRiskProfile_fail_validation() {
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
                .body("{\"riskProfile\": null}")
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-risk-profile", sessionId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("섹터 선택 성공")
    void pickSectors_success() {
        // Create session and pick risk profile
        String sessionId = createSessionAndPickRiskProfile();

        // Pick sectors (2-3 sectors required)
        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickSectorsRequest pickRequest = new PickSectorsRequest(sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
                .log().all()
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-sectors", sessionId)
        .then()
                .log().all()
                .statusCode(200)
                .body("sessionId", equalTo(sessionId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("currentRound", equalTo(3))
                .body("picked", hasSize(2));
    }

    @Test
    @DisplayName("섹터 선택 실패 - 4개 선택 (최대 3개)")
    void pickSectors_fail_tooMany() {
        String sessionId = createSessionAndPickRiskProfile();

        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE,
                Sector.FINANCIALS,
                Sector.ENERGY
        );
        PickSectorsRequest pickRequest = new PickSectorsRequest(sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-sectors", sessionId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("자산 선택 성공")
    void pickAsset_success() {
        // Create session, pick risk profile, and pick sectors
        String sessionId = createSessionAndPickSectors();

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
                .body("currentRound", equalTo(4))
                .body("picked", equalTo(assetId));
    }

    @Test
    @DisplayName("자산 선택 실패 - 유효하지 않은 자산 ID")
    void pickAsset_fail_invalidAssetId() {
        String sessionId = createSessionAndPickSectors();

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
                .body("currentRound", equalTo(1))
                .extract()
                .path("sessionId");

        // 2. Pick risk profile (Round 1)
        PickRiskProfileRequest riskRequest = new PickRiskProfileRequest(RiskProfile.BALANCED);
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(riskRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-risk-profile", sessionId)
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(2));

        // 3. Pick sectors (Round 2)
        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickSectorsRequest sectorsRequest = new PickSectorsRequest(sectors);
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(sectorsRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-sectors", sessionId)
        .then()
                .statusCode(200)
                .body("currentRound", equalTo(3));

        // 4. Pick assets (Rounds 3-12)
        for (int round = 3; round <= 12; round++) {
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
            int expectedNextRound = round < 12 ? round + 1 : 12;
            String expectedStatus = round < 12 ? "IN_PROGRESS" : "COMPLETED";

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

        // 5. Verify session is completed
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/arena/sessions/{sessionId}", sessionId)
        .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("currentRound", equalTo(12))
                .body("selectedAssetIds", hasSize(10));
    }

    // Helper methods

    private String createSessionAndPickRiskProfile() {
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

        PickRiskProfileRequest pickRequest = new PickRiskProfileRequest(RiskProfile.BALANCED);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-risk-profile", sessionId)
        .then()
                .statusCode(200);

        return sessionId;
    }

    private String createSessionAndPickSectors() {
        String sessionId = createSessionAndPickRiskProfile();

        List<Sector> sectors = Arrays.asList(
                Sector.INFORMATION_TECHNOLOGY,
                Sector.HEALTH_CARE
        );
        PickSectorsRequest pickRequest = new PickSectorsRequest(sectors);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(pickRequest)
        .when()
                .post("/arena/sessions/{sessionId}/rounds/current/pick-sectors", sessionId)
        .then()
                .statusCode(200);

        return sessionId;
    }
}