package com.porcana.domain.user;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.domain.user.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class UserControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private String signupAndGetAccessToken() {
        SignupRequest signupRequest = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        return given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/app/v1/auth/signup")
        .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMe_success() {
        String accessToken = signupAndGetAccessToken();

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/app/v1/me")
        .then()
                .log().all()
                .statusCode(200)
                .body("userId", notNullValue())
                .body("nickname", equalTo("테스터"))
                .body("mainPortfolioId", nullValue());
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 인증 없음")
    void getMe_fail_noAuth() {
        given()
        .when()
                .get("/app/v1/me")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 유효하지 않은 토큰")
    void getMe_fail_invalidToken() {
        given()
                .header("Authorization", "Bearer invalid-token")
        .when()
                .get("/app/v1/me")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("내 정보 수정 성공")
    void updateMe_success() {
        String accessToken = signupAndGetAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("새닉네임");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/app/v1/me")
        .then()
                .log().all()
                .statusCode(200)
                .body("userId", notNullValue())
                .body("nickname", equalTo("새닉네임"));
    }

    @Test
    @DisplayName("내 정보 수정 실패 - validation 오류 (빈 닉네임)")
    void updateMe_fail_blankNickname() {
        String accessToken = signupAndGetAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/app/v1/me")
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
                .patch("/app/v1/me")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("내 정보 수정 후 조회하면 변경된 닉네임 확인")
    void updateMe_then_getMe() {
        String accessToken = signupAndGetAccessToken();

        // 닉네임 수정
        UpdateUserRequest updateRequest = new UpdateUserRequest("변경된닉네임");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/app/v1/me")
        .then()
                .statusCode(200);

        // 조회해서 확인
        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/app/v1/me")
        .then()
                .statusCode(200)
                .body("nickname", equalTo("변경된닉네임"));
    }
}
