package com.porcana.domain.portfolio;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.portfolio.dto.UpdateAssetWeightsRequest;
import com.porcana.domain.portfolio.dto.UpdatePortfolioNameRequest;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

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
}