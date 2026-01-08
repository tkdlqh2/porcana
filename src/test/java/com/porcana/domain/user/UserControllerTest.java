package com.porcana.domain.user;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Sql(scripts = "/sql/user-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserControllerTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Test user ID from SQL file
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private String createAccessToken() {
        return jwtTokenProvider.createAccessToken(TEST_USER_ID);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMe_success() {
        String accessToken = createAccessToken();

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .log().all()
                .statusCode(200)
                .body("userId", equalTo(TEST_USER_ID.toString()))
                .body("nickname", equalTo("테스터"))
                .body("mainPortfolioId", nullValue());
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 인증 없음")
    void getMe_fail_noAuth() {
        given()
        .when()
                .get("/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 유효하지 않은 토큰")
    void getMe_fail_invalidToken() {
        given()
                .header("Authorization", "Bearer invalid-token")
        .when()
                .get("/me")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("내 정보 수정 성공")
    void updateMe_success() {
        String accessToken = createAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("새닉네임");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .log().all()
                .statusCode(200)
                .body("userId", equalTo(TEST_USER_ID.toString()))
                .body("nickname", equalTo("새닉네임"));
    }

    @Test
    @DisplayName("내 정보 수정 실패 - validation 오류 (빈 닉네임)")
    void updateMe_fail_blankNickname() {
        String accessToken = createAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("내 정보 수정 실패 - 인증 없음")
    void updateMe_fail_noAuth() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("새닉네임");

        given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("내 정보 수정 후 조회하면 변경된 닉네임 확인")
    void updateMe_then_getMe() {
        String accessToken = createAccessToken();

        // 닉네임 수정
        UpdateUserRequest updateRequest = new UpdateUserRequest("변경된닉네임");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(200);

        // 조회해서 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(200)
                .body("nickname", equalTo("변경된닉네임"));
    }
}
