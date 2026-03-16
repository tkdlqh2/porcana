package com.porcana.domain.portfolio;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.portfolio.dto.DirectCreatePortfolioRequest;
import com.porcana.domain.portfolio.dto.UpdateAssetWeightsRequest;
import com.porcana.domain.portfolio.dto.UpdatePortfolioNameRequest;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Sql(scripts = "/sql/portfolio-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PortfolioControllerTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Test IDs from SQL file
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_PORTFOLIO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TEST_ASSET_KR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_ASSET_US_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private String createAccessToken() {
        return jwtTokenProvider.createAccessToken(TEST_USER_ID);
    }

    @Test
    @DisplayName("포트폴리오 이름 수정 성공")
    void updatePortfolioName_success() {
        String accessToken = createAccessToken();
        UpdatePortfolioNameRequest request = new UpdatePortfolioNameRequest("새로운 포트폴리오 이름");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .patch("/portfolios/{portfolioId}/name", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(200)
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("name", equalTo("새로운 포트폴리오 이름"));
    }

    @Test
    @DisplayName("포트폴리오 이름 수정 실패 - 인증 없음")
    void updatePortfolioName_fail_noAuth() {
        UpdatePortfolioNameRequest request = new UpdatePortfolioNameRequest("새로운 포트폴리오 이름");

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .patch("/portfolios/{portfolioId}/name", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("포트폴리오 이름 수정 실패 - 빈 이름")
    void updatePortfolioName_fail_blankName() {
        String accessToken = createAccessToken();
        UpdatePortfolioNameRequest request = new UpdatePortfolioNameRequest("");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .patch("/portfolios/{portfolioId}/name", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("자산 비중 수정 성공 - 50/50에서 70/30으로")
    void updateAssetWeights_success() {
        String accessToken = createAccessToken();

        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(200)
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("weights", hasSize(2))
                .body("weights[0].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)))
                .body("weights[1].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)));
    }

    @Test
    @DisplayName("자산 비중 수정 성공 - 같은 날 두 번 수정")
    void updateAssetWeights_success_twiceOnSameDay() {
        String accessToken = createAccessToken();

        // 첫 번째 수정: 70/30
        UpdateAssetWeightsRequest request1 = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request1)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200);

        // 두 번째 수정: 60/40 (같은 날)
        UpdateAssetWeightsRequest request2 = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 60.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 40.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request2)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(200)
                .body("weights", hasSize(2))
                .body("weights[0].weightPct", anyOf(equalTo(60.0f), equalTo(40.0f)))
                .body("weights[1].weightPct", anyOf(equalTo(60.0f), equalTo(40.0f)));
    }

    @Test
    @DisplayName("자산 비중 수정 실패 - 비중 합계가 100%가 아님 (90%)")
    void updateAssetWeights_fail_totalNot100() {
        String accessToken = createAccessToken();

        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 50.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 40.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(400);
    }

    @Test
    @DisplayName("자산 비중 수정 실패 - 존재하지 않는 자산")
    void updateAssetWeights_fail_assetNotInPortfolio() {
        String accessToken = createAccessToken();

        String nonExistentAssetId = "99999999-9999-9999-9999-999999999999";

        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 50.00),
                        new UpdateAssetWeightsRequest.AssetWeight(nonExistentAssetId, 50.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("자산 비중 수정 실패 - 인증 없음")
    void updateAssetWeights_fail_noAuth() {
        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("자산 비중 수정 후 포트폴리오 조회 시 변경된 비중 확인")
    void updateAssetWeights_then_getPortfolio() {
        String accessToken = createAccessToken();

        // 비중 수정: 70/30
        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200);

        // 포트폴리오 조회해서 비중 확인 (즉시 반영 확인)
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(200)
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("positions", hasSize(2))
                // 비중이 즉시 반영되어야 함 (PortfolioAsset fallback)
                .body("positions.weightPct", hasItems(70.0f, 30.0f));
    }

    @Test
    @DisplayName("자산 비중 수정 후 모든 Read API에서 새 비중 반영 확인 (상세/리스트/홈)")
    void updateAssetWeights_reflectedInAllReadApis() {
        String accessToken = createAccessToken();

        // 1. 비중 수정: 70/30
        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200)
                .body("weights", hasSize(2))
                .body("weights[0].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)))
                .body("weights[1].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)));

        // 2. 포트폴리오 상세 조회에서 새 비중 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(200)
                .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("positions", hasSize(2))
                .body("positions.weightPct", hasItems(70.0f, 30.0f));

        // 3. 포트폴리오 리스트 조회에서 새 비중 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios")
        .then()
                .log().all()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                // 테스트 포트폴리오 찾기
                .body("find { it.portfolioId == '" + TEST_PORTFOLIO_ID + "' }.topAssets", hasSize(2))
                .body("find { it.portfolioId == '" + TEST_PORTFOLIO_ID + "' }.topAssets.weight", hasItems(70.0f, 30.0f));

        // 4. 메인 포트폴리오로 설정
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .put("/portfolios/{portfolioId}/main", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200)
                .body("mainPortfolioId", equalTo(TEST_PORTFOLIO_ID.toString()));

        // 5. 홈 화면 조회에서 새 비중 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/home")
        .then()
                .log().all()
                .statusCode(200)
                .body("hasMainPortfolio", equalTo(true))
                .body("mainPortfolio.portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                .body("positions", hasSize(2))
                .body("positions.weightPct", hasItems(70.0f, 30.0f));
    }

    @Test
    @DisplayName("일별 수익률로 비중이 변경된 포트폴리오에서 비중 재조정 후 모든 Read API 반영 확인")
    @Sql(scripts = "/sql/portfolio-with-daily-returns-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void updateAssetWeights_afterDailyReturns_reflectedInAllReadApis() {
        // Test user and portfolio from the daily returns SQL
        UUID testUserId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
        UUID testPortfolioId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID testAssetKrId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID testAssetUsId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        String accessToken = jwtTokenProvider.createAccessToken(testUserId);

        // 1. 비중 변경 전 상태 확인: weightUsed가 55/45로 변경되어 있어야 함
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", testPortfolioId)
        .then()
                .log().all()
                .statusCode(200)
                .body("positions", hasSize(2))
                // weightUsed가 시장 변동으로 55/45로 변경된 상태
                .body("positions.weightPct", hasItems(55.0f, 45.0f));

        // 2. 비중 재조정: 70/30으로 변경
        UpdateAssetWeightsRequest request = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(testAssetKrId.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(testAssetUsId.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .put("/portfolios/{portfolioId}/weights", testPortfolioId)
        .then()
                .log().all()
                .statusCode(200)
                .body("weights", hasSize(2))
                .body("weights[0].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)))
                .body("weights[1].weightPct", anyOf(equalTo(70.0f), equalTo(30.0f)));

        // 3. 포트폴리오 상세 조회에서 새 비중 70/30 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", testPortfolioId)
        .then()
                .log().all()
                .statusCode(200)
                .body("portfolioId", equalTo(testPortfolioId.toString()))
                .body("positions", hasSize(2))
                // 재조정된 비중 70/30이 반영되어야 함
                .body("positions.weightPct", hasItems(70.0f, 30.0f));

        // 4. 포트폴리오 리스트 조회에서 새 비중 70/30 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios")
        .then()
                .log().all()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("find { it.portfolioId == '" + testPortfolioId + "' }.topAssets", hasSize(2))
                .body("find { it.portfolioId == '" + testPortfolioId + "' }.topAssets.weight", hasItems(70.0f, 30.0f));

        // 5. 메인 포트폴리오로 설정
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .put("/portfolios/{portfolioId}/main", testPortfolioId)
        .then()
                .statusCode(200)
                .body("mainPortfolioId", equalTo(testPortfolioId.toString()));

        // 6. 홈 화면 조회에서 새 비중 70/30 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/home")
        .then()
                .log().all()
                .statusCode(200)
                .body("hasMainPortfolio", equalTo(true))
                .body("mainPortfolio.portfolioId", equalTo(testPortfolioId.toString()))
                .body("positions", hasSize(2))
                // 재조정된 비중 70/30이 홈 화면에도 반영되어야 함
                .body("positions.weightPct", hasItems(70.0f, 30.0f));
    }

    @Test
    @DisplayName("포트폴리오 삭제 성공 - 회원")
    void deletePortfolio_success() {
        String accessToken = createAccessToken();

        // 삭제 전 포트폴리오 조회 성공
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200);

        // 포트폴리오 삭제
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .log().all()
                .statusCode(204);

        // 삭제 후 포트폴리오 조회 실패 (404 또는 400)
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("포트폴리오 삭제 성공 - 게스트")
    @Sql(scripts = "/sql/portfolio-guest-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void deletePortfolio_success_guest() {
        UUID guestSessionId = UUID.fromString("aaa00000-0000-0000-0000-000000000001");
        UUID guestPortfolioId = UUID.fromString("bbb00000-0000-0000-0000-000000000001");

        // 삭제 전 포트폴리오 조회 성공
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/portfolios/{portfolioId}", guestPortfolioId)
        .then()
                .statusCode(200);

        // 포트폴리오 삭제
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .delete("/portfolios/{portfolioId}", guestPortfolioId)
        .then()
                .log().all()
                .statusCode(204);

        // 삭제 후 포트폴리오 조회 실패
        given()
                .header("X-Guest-Session-Id", guestSessionId.toString())
        .when()
                .get("/portfolios/{portfolioId}", guestPortfolioId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("포트폴리오 삭제 실패 - 권한 없음")
    void deletePortfolio_fail_noPermission() {
        String accessToken = createAccessToken();
        UUID otherPortfolioId = UUID.randomUUID();

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/portfolios/{portfolioId}", otherPortfolioId)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("메인 포트폴리오 삭제 실패 - 메인 변경 필요")
    void deletePortfolio_fail_mainPortfolio() {
        String accessToken = createAccessToken();

        // 메인 포트폴리오로 설정
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .put("/portfolios/{portfolioId}/main", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(200);

        // 메인 포트폴리오 삭제 시도 - 실패해야 함
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("포트폴리오 삭제 후 목록에서 제외됨")
    void deletePortfolio_notInList() {
        String accessToken = createAccessToken();

        // 삭제 전 목록 조회 - 포트폴리오가 있음
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios")
        .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("find { it.portfolioId == '" + TEST_PORTFOLIO_ID + "' }", notNullValue());

        // 포트폴리오 삭제
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(204);

        // 삭제 후 목록 조회 - 포트폴리오가 없음
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/portfolios")
        .then()
                .log().all()
                .statusCode(200)
                .body("find { it.portfolioId == '" + TEST_PORTFOLIO_ID + "' }", nullValue());
    }

    @Test
    @DisplayName("삭제된 포트폴리오 수정 불가")
    void deletePortfolio_cannotUpdate() {
        String accessToken = createAccessToken();

        // 포트폴리오 삭제
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/portfolios/{portfolioId}", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(204);

        // 삭제 후 이름 수정 시도
        UpdatePortfolioNameRequest request = new UpdatePortfolioNameRequest("새 이름");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .patch("/portfolios/{portfolioId}/name", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);

        // 삭제 후 비중 수정 시도
        UpdateAssetWeightsRequest weightRequest = new UpdateAssetWeightsRequest(
                List.of(
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_KR_ID.toString(), 70.00),
                        new UpdateAssetWeightsRequest.AssetWeight(TEST_ASSET_US_ID.toString(), 30.00)
                )
        );

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(weightRequest)
        .when()
                .put("/portfolios/{portfolioId}/weights", TEST_PORTFOLIO_ID)
        .then()
                .statusCode(400);
    }

    @Nested
    @DisplayName("POST /portfolios/direct - 포트폴리오 직접 생성")
    @Sql(scripts = "/sql/portfolio-direct-create-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    class DirectCreatePortfolioTest {

        private static final UUID DIRECT_TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        private static final UUID GUEST_SESSION_ID = UUID.fromString("ccc00000-0000-0000-0000-000000000001");

        private static final UUID ASSET_1 = UUID.fromString("d1111111-1111-1111-1111-111111111111");
        private static final UUID ASSET_2 = UUID.fromString("d2222222-2222-2222-2222-222222222222");
        private static final UUID ASSET_3 = UUID.fromString("d3333333-3333-3333-3333-333333333333");
        private static final UUID ASSET_4 = UUID.fromString("d4444444-4444-4444-4444-444444444444");
        private static final UUID ASSET_5 = UUID.fromString("d5555555-5555-5555-5555-555555555555");
        private static final UUID ASSET_6 = UUID.fromString("d6666666-6666-6666-6666-666666666666");
        private static final UUID ASSET_7 = UUID.fromString("d7777777-7777-7777-7777-777777777777");

        private String createAccessToken() {
            return jwtTokenProvider.createAccessToken(DIRECT_TEST_USER_ID);
        }

        @Test
        @DisplayName("성공 - 비중 직접 입력 (5종목)")
        void createPortfolioDirect_success_withWeights() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "직접 생성 포트폴리오",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(30)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, BigDecimal.valueOf(25)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, BigDecimal.valueOf(15)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, BigDecimal.valueOf(10))
                    )
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", notNullValue())
                    .body("name", equalTo("직접 생성 포트폴리오"))
                    .body("status", equalTo("ACTIVE"));
        }

        @Test
        @DisplayName("성공 - 균등 배분 (비중 생략)")
        void createPortfolioDirect_success_equalWeight() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "균등 배분 포트폴리오",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, null)
                    )
            );

            String portfolioId = given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", notNullValue())
                    .body("status", equalTo("ACTIVE"))
                    .extract().path("portfolioId");

            // 포트폴리오 상세 조회해서 균등 배분 확인 (각 20%)
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}", portfolioId)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("positions", hasSize(5))
                    .body("positions.weightPct", everyItem(equalTo(20.0f)));
        }

        @Test
        @DisplayName("성공 - 게스트 사용자")
        void createPortfolioDirect_success_guest() {
            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "게스트 포트폴리오",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, BigDecimal.valueOf(20))
                    )
            );

            given()
                    .header("X-Guest-Session-Id", GUEST_SESSION_ID.toString())
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", notNullValue())
                    .body("status", equalTo("ACTIVE"));
        }

        @Test
        @DisplayName("실패 - 종목 개수 부족 (4개)")
        void createPortfolioDirect_fail_tooFewAssets() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "종목 부족",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(25)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, BigDecimal.valueOf(25)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(25)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, BigDecimal.valueOf(25))
                    )
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 비중 합계가 100%가 아님")
        void createPortfolioDirect_fail_weightNot100() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "비중 오류",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(30)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, BigDecimal.valueOf(30)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, BigDecimal.valueOf(10)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, BigDecimal.valueOf(5))
                    )
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 비중 일부만 입력")
        void createPortfolioDirect_fail_partialWeights() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "일부 비중만",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(50)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(30)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, BigDecimal.valueOf(20))
                    )
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 종목")
        void createPortfolioDirect_fail_assetNotFound() {
            String accessToken = createAccessToken();
            UUID nonExistentAsset = UUID.randomUUID();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "존재하지 않는 종목",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, BigDecimal.valueOf(20)),
                            new DirectCreatePortfolioRequest.AssetInput(nonExistentAsset, BigDecimal.valueOf(20))
                    )
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .log().all()
                    .statusCode(400);
        }

        @Test
        @DisplayName("성공 - 생성 후 포트폴리오 목록에서 조회됨")
        void createPortfolioDirect_appearsInList() {
            String accessToken = createAccessToken();

            DirectCreatePortfolioRequest request = new DirectCreatePortfolioRequest(
                    "목록 확인용",
                    List.of(
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_1, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_2, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_3, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_4, null),
                            new DirectCreatePortfolioRequest.AssetInput(ASSET_5, null)
                    )
            );

            String portfolioId = given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/direct")
            .then()
                    .statusCode(200)
                    .extract().path("portfolioId");

            // 목록에서 조회
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios")
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("find { it.portfolioId == '" + portfolioId + "' }", notNullValue())
                    .body("find { it.portfolioId == '" + portfolioId + "' }.status", equalTo("ACTIVE"));
        }
    }
}