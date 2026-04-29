package com.porcana.domain.user;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;

@Sql(scripts = "/sql/user-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserControllerTest extends BaseIntegrationTest {

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private String createAccessToken() {
        return jwtTokenProvider.createAccessToken(TEST_USER_ID, "USER");
    }

    @Test
    @DisplayName("get me success")
    void getMe_success() {
        String accessToken = createAccessToken();

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(200)
                .body("userId", equalTo(TEST_USER_ID.toString()))
                .body("nickname", equalTo("tester"))
                .body("emailVerified", equalTo(false))
                .body("mainPortfolioId", nullValue());
    }

    @Test
    @DisplayName("get me fails without auth")
    void getMe_fail_noAuth() {
        given()
        .when()
                .get("/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("get me fails with invalid token")
    void getMe_fail_invalidToken() {
        given()
                .header("Authorization", "Bearer invalid-token")
        .when()
                .get("/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("update me success")
    void updateMe_success() {
        String accessToken = createAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("updated-user");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(200)
                .body("userId", equalTo(TEST_USER_ID.toString()))
                .body("emailVerified", equalTo(false))
                .body("nickname", equalTo("updated-user"));
    }

    @Test
    @DisplayName("update me fails on blank nickname")
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
    @DisplayName("update me fails without auth")
    void updateMe_fail_noAuth() {
        UpdateUserRequest updateRequest = new UpdateUserRequest("updated-user");

        given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("update me persists")
    void updateMe_then_getMe() {
        String accessToken = createAccessToken();

        UpdateUserRequest updateRequest = new UpdateUserRequest("persisted-user");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(updateRequest)
        .when()
                .patch("/me")
        .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(200)
                .body("nickname", equalTo("persisted-user"));
    }

    @Test
    @DisplayName("delete me success")
    void deleteMe_success() {
        String accessToken = createAccessToken();

        assertTrue(userRepository.existsById(TEST_USER_ID));
        assertTrue(userRepository.findByIdAndDeletedAtIsNull(TEST_USER_ID).isPresent());

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .delete("/me")
        .then()
                .statusCode(204);

        User deletedUser = userRepository.findById(TEST_USER_ID).orElseThrow();
        assertTrue(deletedUser.isDeleted());
        assertTrue(userRepository.findByIdAndDeletedAtIsNull(TEST_USER_ID).isEmpty());

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me")
        .then()
                .statusCode(401);
    }
}
