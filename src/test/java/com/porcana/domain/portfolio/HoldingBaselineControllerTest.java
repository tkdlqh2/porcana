package com.porcana.domain.portfolio;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.portfolio.dto.baseline.ExecuteTopUpRequest;
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
import java.util.List;
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
    private static final UUID TEST_ASSET_KR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEST_ASSET_US_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab");

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
    @DisplayName("POST /portfolios/{portfolioId}/top-up - 추가 입금 실행")
    class ExecuteTopUpTest {

        @Test
        @DisplayName("성공 - 추가 입금 실행 후 baseline 업데이트")
        void executeTopUp_success() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 추가 입금 실행
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    true
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("portfolioId", equalTo(TEST_PORTFOLIO_ID.toString()))
                    .body("baseCurrency", equalTo("KRW"))
                    .body("summary.additionalCash", equalTo(2000000))
                    .body("summary.totalPurchaseAmount", notNullValue())
                    .body("summary.remainingCash", notNullValue())
                    .body("summary.newCashAmount", notNullValue())
                    .body("updatedItems", hasSize(1))
                    .body("updatedItems[0].symbol", equalTo("BASELINE_KR"))
                    .body("updatedItems[0].addedQuantity", equalTo(10));
        }

        @Test
        @DisplayName("성공 - 여러 종목 동시 매수")
        void executeTopUp_multipleAssets() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 여러 종목 추가 입금 실행
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("3000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            ),
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_US_ID,
                                    new BigDecimal("5"),
                                    new BigDecimal("232")
                            )
                    ),
                    false
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("updatedItems", hasSize(2));
        }

        @Test
        @DisplayName("실패 - Baseline 없이 추가 입금 실행")
        void executeTopUp_fail_noBaseline() {
            String accessToken = createAccessToken();

            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    false
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - Baseline에 없는 자산 매수 시도")
        void executeTopUp_fail_invalidAsset() {
            String accessToken = createAccessToken();

            // 먼저 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 존재하지 않는 자산 ID로 요청
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    UUID.randomUUID(),  // 존재하지 않는 자산
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    false
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 추가 금액 null")
        void executeTopUp_fail_nullAmount() {
            String accessToken = createAccessToken();

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body("""
                        {
                            "additionalCash": null,
                            "purchases": [
                                {"assetId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "quantity": 10, "purchasePrice": 71000}
                            ]
                        }
                        """)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 매수 내역 빈 배열")
        void executeTopUp_fail_emptyPurchases() {
            String accessToken = createAccessToken();

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body("""
                        {
                            "additionalCash": 2000000,
                            "purchases": []
                        }
                        """)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("실패 - 인증 없음")
        void executeTopUp_fail_noAuth() {
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    false
            );

            given()
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(403);
        }

        @Test
        @DisplayName("검증 - 실행 후 baseline 상태 변경 확인")
        void executeTopUp_verifiesBaselineUpdated() {
            String accessToken = createAccessToken();

            // 1. 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 2. 초기 baseline 상태 확인 (KR 자산의 수량 기록)
            Number initialQuantity = given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(200)
                    .extract()
                    .path("items.find { it.symbol == 'BASELINE_KR' }.quantity");

            // 3. 추가 입금 실행 (10주 추가)
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    false
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(200);

            // 4. baseline 다시 조회하여 수량 증가 확인
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("items.find { it.symbol == 'BASELINE_KR' }.quantity",
                            equalTo(initialQuantity.floatValue() + 10f));
        }

        @Test
        @DisplayName("검증 - 가중 평균 단가 계산 정확성")
        void executeTopUp_calculatesWeightedAverageCorrectly() {
            String accessToken = createAccessToken();

            // 1. 시드 설정 (초기 avgPrice = 71000)
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 2. 초기 수량 확인 (약 70주 @ 71000원)
            Number initialQuantity = given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .extract()
                    .path("items.find { it.symbol == 'BASELINE_KR' }.quantity");

            // 3. 30주를 75000원에 추가 매수
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("3000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("30"),
                                    new BigDecimal("75000")  // 다른 가격으로 매수
                            )
                    ),
                    false
            );

            // 4. 응답에서 가중 평균 확인
            // 기존: 70주 @ 71000원 (시드 설정 시 현재가로 고정)
            // 추가: 30주 @ 75000원
            // 새 평균 = (70 * 71000 + 30 * 75000) / 100 = 72200원
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .log().all()
                    .statusCode(200)
                    .body("updatedItems[0].previousAvgPrice", equalTo(71000.0f))
                    .body("updatedItems[0].newAvgPrice", equalTo(72200.0f));
        }

        @Test
        @DisplayName("검증 - addRemainingCashToBaseline=true 시 현금 추가")
        void executeTopUp_addRemainingCashTrue() {
            String accessToken = createAccessToken();

            // 1. 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 2. 초기 현금 확인
            Number initialCash = given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .extract()
                    .path("cashAmount");

            // 3. 2,000,000원 추가 입금, 710,000원만 사용 (10주 * 71000원)
            // 남은 1,290,000원이 현금에 추가되어야 함
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    true  // 남은 현금 baseline에 추가
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(200)
                    .body("summary.totalPurchaseAmount", equalTo(710000))
                    .body("summary.remainingCash", equalTo(1290000));

            // 4. baseline 현금이 증가했는지 확인
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .body("cashAmount", equalTo(initialCash.floatValue() + 1290000f));
        }

        @Test
        @DisplayName("검증 - addRemainingCashToBaseline=false 시 현금 유지")
        void executeTopUp_addRemainingCashFalse() {
            String accessToken = createAccessToken();

            // 1. 시드 설정
            SetSeedRequest setSeedRequest = new SetSeedRequest(new BigDecimal("10000000"), "KRW");
            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(setSeedRequest)
            .when()
                    .put("/portfolios/{portfolioId}/seed", TEST_PORTFOLIO_ID);

            // 2. 초기 현금 확인
            Number initialCash = given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .extract()
                    .path("cashAmount");

            // 3. addRemainingCashToBaseline = false
            ExecuteTopUpRequest request = new ExecuteTopUpRequest(
                    new BigDecimal("2000000"),
                    List.of(
                            new ExecuteTopUpRequest.PurchaseItem(
                                    TEST_ASSET_KR_ID,
                                    new BigDecimal("10"),
                                    new BigDecimal("71000")
                            )
                    ),
                    false  // 남은 현금 baseline에 추가 안 함
            );

            given()
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(ContentType.JSON)
                    .body(request)
            .when()
                    .post("/portfolios/{portfolioId}/top-up", TEST_PORTFOLIO_ID)
            .then()
                    .statusCode(200);

            // 4. baseline 현금이 변하지 않았는지 확인
            given()
                    .header("Authorization", "Bearer " + accessToken)
            .when()
                    .get("/portfolios/{portfolioId}/holding-baseline", TEST_PORTFOLIO_ID)
            .then()
                    .body("cashAmount", equalTo(initialCash.floatValue()));
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
                    .body("baseCurrency", notNullValue())
                    .body("summary.totalValue", notNullValue())
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
