package com.porcana.domain.admin.controller;

import com.porcana.BaseIntegrationTest;
import com.porcana.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Sql(scripts = "/sql/admin-api-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminControllerTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final UUID ADMIN_USER_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVE_PORTFOLIO_ID = UUID.fromString("a7490f7e-8596-41ff-abb0-ff06894928f2");
    private static final UUID KR_ASSET_ID = UUID.fromString("f1111111-1111-1111-1111-111111111111");

    private String createAdminAccessToken() {
        return jwtTokenProvider.createAccessToken(ADMIN_USER_ID, "ADMIN");
    }

    @Test
    @DisplayName("관리자 포트폴리오 목록 기본 조회 시 DRAFT 제외")
    void getPortfolios_defaultExcludesDraft() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
        .when()
                .get("/admin/portfolios")
        .then()
                .statusCode(200)
                .body("portfolios", hasSize(2))
                .body("portfolios.find { it.status == 'DRAFT' }", nullValue())
                .body("portfolios.status", everyItem(in(java.util.List.of("ACTIVE", "FINISHED"))));
    }

    @Test
    @DisplayName("관리자 포트폴리오 목록 상태 필터 ACTIVE")
    void getPortfolios_filterByActive() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .queryParam("status", "ACTIVE")
        .when()
                .get("/admin/portfolios")
        .then()
                .statusCode(200)
                .body("portfolios", hasSize(1))
                .body("portfolios[0].portfolioId", equalTo(ACTIVE_PORTFOLIO_ID.toString()))
                .body("portfolios[0].status", equalTo("ACTIVE"));
    }

    @Test
    @DisplayName("관리자 포트폴리오 목록 상태 필터 DRAFT 거부")
    void getPortfolios_rejectsDraftFilter() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .queryParam("status", "DRAFT")
        .when()
                .get("/admin/portfolios")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("관리자 포트폴리오 수익률 차트 조회")
    void getPortfolioPerformance_admin() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .queryParam("range", "1M")
        .when()
                .get("/admin/portfolios/{portfolioId}/performance", ACTIVE_PORTFOLIO_ID)
        .then()
                .statusCode(200)
                .body("portfolioId", equalTo(ACTIVE_PORTFOLIO_ID.toString()))
                .body("range", equalTo("1M"))
                .body("points.size()", greaterThanOrEqualTo(2))
                .body("points[0].value", equalTo(100.0f));
    }

    @Test
    @DisplayName("관리자 종목 상세 조회")
    void getAssetDetail_admin() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
        .when()
                .get("/admin/assets/{assetId}", KR_ASSET_ID)
        .then()
                .statusCode(200)
                .body("assetId", equalTo(KR_ASSET_ID.toString()))
                .body("ticker", equalTo("ADMIN_KR"))
                .body("name", equalTo("Admin KR Asset"))
                .body("description", equalTo("KR admin asset description"))
                .body("currentRiskLevel", equalTo(3))
                .body("personality", notNullValue());
    }

    @Test
    @DisplayName("관리자 종목 차트 조회")
    void getAssetChart_admin() {
        given()
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .queryParam("range", "1M")
        .when()
                .get("/admin/assets/{assetId}/chart", KR_ASSET_ID)
        .then()
                .statusCode(200)
                .body("assetId", equalTo(KR_ASSET_ID.toString()))
                .body("range", equalTo("1M"))
                .body("points", hasSize(2))
                .body("points[0].close", equalTo(10050.0f));
    }
}
