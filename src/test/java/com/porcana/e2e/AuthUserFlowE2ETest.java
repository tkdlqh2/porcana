package com.porcana.e2e;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.auth.dto.RefreshRequest;
import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.auth.repository.EmailVerificationTokenRepository;
import com.porcana.domain.auth.repository.PasswordResetTokenRepository;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUserFlowE2ETest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    void cleanUp() {
        passwordResetTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원 가입부터 탈퇴까지 주요 인증/사용자 흐름 E2E")
    void authAndUserLifecycle_e2e() {
        SignupRequest signupRequest = new SignupRequest(
                "e2e-user@example.com",
                "password123",
                "e2e-user"
        );

        ExtractableResponse<Response> signupResponse = given()
                .contentType(ContentType.JSON)
                .body(signupRequest)
        .when()
                .post("/auth/signup")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("user.email", equalTo("e2e-user@example.com"))
                .body("user.nickname", equalTo("e2e-user"))
                .extract();

        String accessToken = signupResponse.path("accessToken");
        String refreshToken = signupResponse.path("refreshToken");

        User createdUser = userRepository.findByEmailAndDeletedAtIsNull("e2e-user@example.com")
                .orElseThrow();

        assertTrue(emailVerificationTokenRepository.findAll().stream()
                .anyMatch(token -> token.getUser().getId().equals(createdUser.getId())));

        given()
                .param("email", "e2e-user@example.com")
        .when()
                .get("/auth/check-email")
        .then()
                .statusCode(200)
                .body("available", equalTo(false));

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(200)
                .body("userId", equalTo(createdUser.getId().toString()))
                .body("email", equalTo("e2e-user@example.com"))
                .body("nickname", equalTo("e2e-user"));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(new UpdateUserRequest("e2e-user-updated"))
        .when()
                .patch("/me")
        .then()
                .statusCode(200)
                .body("nickname", equalTo("e2e-user-updated"));

        String rotatedRefreshToken = given()
                .contentType(ContentType.JSON)
                .body(new RefreshRequest(refreshToken))
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract()
                .path("refreshToken");

        given()
                .contentType(ContentType.JSON)
                .body(new RefreshRequest(rotatedRefreshToken))
        .when()
                .post("/auth/refresh")
        .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/me")
        .then()
                .statusCode(204);

        User deletedUser = userRepository.findByEmail("e2e-user@example.com").orElseThrow();
        assertTrue(deletedUser.isDeleted());

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(401);
    }
}
