package com.porcana.domain.portfolio;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.portfolio.dto.baseline.RebalancingPlanRequest;
import com.porcana.domain.portfolio.dto.baseline.SetSeedRequest;
import com.porcana.domain.portfolio.dto.baseline.TopUpPlanRequest;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Sql(scripts = "/sql/holding-baseline-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class HoldingBaselineControllerTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Test IDs from SQL file
    private static final UUID TEST_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TEST_PORTFOLIO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private String createAccessToken() {
        return jwtTokenProvider.createAccessToken(TEST_USER_ID);
    }

    @Nested
    @DisplayName("PUT /portfolios/{portfolioId}/seed - 시드 금액 설정")
    class SetSeedTest {

        @Test
        @DisplayName("성공 - 1000만원 시드 설정")
        void setSeed_success() {
            String accessToken = createAccessToken();
            SetSeedRequest request = new SetSeedRequest(new BigDecimal("10000000"), "KRW");

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("exists", equalTo(true))
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("sourceType", equalTo("SEEDED"))
                    .body("baseCurrency", equalTo("KRW"))
                    .body("items", hasSize(2))
                    .body("items[0].quantity", notNullValue())
                    .body("cashAmount", notNullValue());
        }

        @Test
        @DisplayName("실패 - 인증 없음")
        void setSeed_fail_noAuth() {
            SetSeedRequest request = new SetSeedRequest(new BigDecimal("10000000"), "KRW");

            given()
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("실패 - 시드 금액 0원")
        void setSeed_fail_zeroSeed() {
            String accessToken = createAccessToken();
            SetSeedRequest request = new SetSeedRequest(BigDecimal.ZERO, "KRW");

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 시드 금액 null")
        void setSeed_fail_nullSeed() {
            String accessToken = createAccessToken();
            SetSeedRequest request = new SetSeedRequest(null, "KRW");

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }
    }

    @Nested
    @DisplayName("GET /portfolios/{portfolioId}/holding-baseline - Baseline 조회")
    class GetBaselineTest {

        @Test
        @DisplayName("성공 - Baseline이 없는 경우")
        void getBaseline_notExists() {
            String accessToken = createAccessToken();

            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("exists", equalTo(false));
        }

        @Test
        @DisplayName("성공 - Baseline이 있는 경우 (시드 설정 후)")
        void getBaseline_exists() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // Baseline 조회
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("exists", equalTo(true))
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("items", hasSize(2));
        }
    }

    @Nested
    @DisplayName("POST /portfolios/{portfolioId}/top-up-plan - 추가 입금 추천")
    class TopUpPlanTest {

        @Test
        @DisplayName("성공 - 시드 설정 후 추가 입금 추천")
        void topUpPlan_success() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 추가 입금 추천 요청
            TopUpPlanRequest topUpRequest = new TopUpPlanRequest(new BigDecimal("2000000"));
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(topUpRequest)
            .when()
                    .post("/portfolios/{portfolioId}/top-up-plan", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("additionalCash", equalTo(2000000))
                    .body("recommendations", notNullValue())
                    .body("remainingCash", notNullValue());
        }

        @Test
        @DisplayName("실패 - Baseline 없이 추가 입금 추천 요청")
        void topUpPlan_fail_noBaseline() {
            String accessToken = createAccessToken();

            TopUpPlanRequest request = new TopUpPlanRequest(new BigDecimal("2000000"));
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up-plan", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);  // IllegalStateException -> 500
        }

        @Test
        @DisplayName("실패 - 추가 금액 0원")
        void topUpPlan_fail_zeroAmount() {
            String accessToken = createAccessToken();

            TopUpPlanRequest request = new TopUpPlanRequest(BigDecimal.ZERO);
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up-plan", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }
    }

    @Nested
    @DisplayName("GET /portfolios/{portfolioId}/rebalance-status - 리밸런싱 상태 조회")
    class RebalanceStatusTest {

        @Test
        @DisplayName("성공 - Baseline 없는 경우")
        void rebalanceStatus_noBaseline() {
            String accessToken = createAccessToken();

            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/rebalance-status", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("hasBaseline", equalTo(false))
                    .body("needsRebalancing", equalTo(false));
        }

        @Test
        @DisplayName("성공 - Baseline 있는 경우 (시드 설정 후)")
        void rebalanceStatus_withBaseline() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 리밸런싱 상태 조회
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/rebalance-status", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("hasBaseline", equalTo(true))
                    .body("thresholdPct", equalTo(5.0f))
                    .body("summary.totalValueKrw", notNullValue())
                    .body("items", hasSize(2));
        }

        @Test
        @DisplayName("성공 - 커스텀 임계값으로 조회")
        void rebalanceStatus_customThreshold() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 커스텀 임계값으로 리밸런싱 상태 조회
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .queryParam("thresholdPct", "10.0")
            .when()
                    .get("/portfolios/{portfolioId}/rebalance-status", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("thresholdPct", equalTo(10.0f));
        }
    }

    @Nested
    @DisplayName("POST /portfolios/{portfolioId}/rebalancing-plan - 리밸런싱 플랜")
    class RebalancingPlanTest {

        @Test
        @DisplayName("성공 - Baseline 없는 경우")
        void rebalancingPlan_noBaseline() {
            String accessToken = createAccessToken();

            RebalancingPlanRequest request = new RebalancingPlanRequest(new BigDecimal("5.0"));
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/rebalancing-plan", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("needsRebalancing", equalTo(false));
        }

        @Test
        @DisplayName("성공 - Baseline 있는 경우 (시드 설정 후)")
        void rebalancingPlan_withBaseline() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 리밸런싱 플랜 요청
            RebalancingPlanRequest request = new RebalancingPlanRequest(new BigDecimal("5.0"));
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/rebalancing-plan", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("thresholdPct", equalTo(5.0f));
        }

        @Test
        @DisplayName("성공 - 기본 임계값 사용")
        void rebalancingPlan_defaultThreshold() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 빈 request body로 리밸런싱 플랜 요청 (기본 임계값 5.0 사용)
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body("{}")
            .when()
                    .post("/portfolios/{portfolioId}/rebalancing-plan", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("thresholdPct", equalTo(5.0f));
        }
    }
}
