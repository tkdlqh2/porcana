package com.porcana.domain.auth;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.auth.dto.LoginRequest;
import com.porcana.domain.auth.dto.RefreshRequest;
import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.user.repository.UserRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .log().all()
        .when()
                .post("/auth/signup")
        .then()
                .log().all()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_fail_duplicateEmail() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        // 첫 번째 회원가입
        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/auth/signup");

        // 두 번째 회원가입 (중복)
        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("회원가입 실패 - validation 오류 (잘못된 이메일)")
    void signup_fail_invalidEmail() {
        SignupRequest request = new SignupRequest(
                "invalid-email",
                "password123",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("회원가입 실패 - validation 오류 (짧은 비밀번호)")
    void signup_fail_shortPassword() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "short",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("회원가입 실패 - validation 오류 (짧은 닉네임)")
    void signup_fail_shortNickname() {
        SignupRequest request = new SignupRequest(
                "test@example.com",
                "password123",
                "a"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        // 회원가입 먼저
        SignupRequest signupRequest = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/auth/signup");

        // 로그인
        LoginRequest loginRequest = new LoginRequest(
                null,
                null,
                "test@example.com",
                "password123"
        );

        given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_fail_wrongPassword() {
        // 회원가입 먼저
        SignupRequest signupRequest = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/auth/signup");

        // 로그인 (잘못된 비밀번호)
        LoginRequest loginRequest = new LoginRequest(
                null,
                null,
                "test@example.com",
                "wrongpassword"
        );

        given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 사용자")
    void login_fail_userNotFound() {
        LoginRequest loginRequest = new LoginRequest(
                null,
                null,
                "notexist@example.com",
                "password123"
        );

        given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("이메일 중복 체크 - 사용 가능")
    void checkEmail_available() {
        given()
                .param("email", "available@example.com")
        .when()
                .get("/auth/check-email")
        .then()
                .statusCode(200)
                .body("available", is(true));
    }

    @Test
    @DisplayName("이메일 중복 체크 - 이미 사용중")
    void checkEmail_notAvailable() {
        // 회원가입 먼저
        SignupRequest signupRequest = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/auth/signup");

        // 중복 체크
        given()
                .param("email", "test@example.com")
        .when()
                .get("/auth/check-email")
        .then()
                .statusCode(200)
                .body("available", is(false));
    }

    @Test
    @DisplayName("토큰 갱신 성공")
    void refresh_success() {
        // 회원가입으로 토큰 받기
        SignupRequest signupRequest = new SignupRequest(
                "test@example.com",
                "password123",
                "테스터"
        );

        String refreshToken = given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(200)
                .extract()
                .path("refreshToken");

        // 토큰 갱신
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);

        given()
                .contentType(ContentType.JSON)
                .body(refreshRequest)
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    @DisplayName("토큰 갱신 실패 - 유효하지 않은 토큰")
    void refresh_fail_invalidToken() {
        RefreshRequest refreshRequest = new RefreshRequest("invalid-token");

        given()
                .contentType(ContentType.JSON)
                .body(refreshRequest)
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(400);
    }
}