package com.porcana.domain.inquiry;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.inquiry.dto.CreateInquiryRequest;
import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryCategory;
import com.porcana.domain.inquiry.repository.InquiryRepository;
import com.porcana.domain.inquiry.repository.InquiryResponseRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import com.porcana.global.security.JwtTokenProvider;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class InquiryControllerTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("73000000-0000-0000-0000-000000000001");

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryResponseRepository inquiryResponseRepository;

    @BeforeEach
    void setUp() {
        inquiryResponseRepository.deleteAll();
        inquiryRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email("inquiry-user@example.com")
                .password("password123")
                .nickname("inquiry-user")
                .provider(User.AuthProvider.EMAIL)
                .build());
    }

    @Test
    @DisplayName("guest can create inquiry")
    void createInquiry_guest() {
        CreateInquiryRequest request = new CreateInquiryRequest(
                "guest@example.com",
                InquiryCategory.GENERAL,
                "Need help",
                "I have a question about the app."
        );

        given()
                .contentType(ContentType.JSON)
                .header("X-Guest-Session-Id", "73000000-0000-0000-0000-000000000099")
                .body(request)
        .when()
                .post("/inquiries")
        .then()
                .statusCode(201)
                .body("inquiryId", notNullValue())
                .body("email", equalTo("guest@example.com"))
                .body("category", equalTo("GENERAL"))
                .body("status", equalTo("RECEIVED"));
    }

    @Test
    @DisplayName("authenticated user can create inquiry")
    void createInquiry_authenticated() {
        User user = userRepository.findByEmailAndDeletedAtIsNull("inquiry-user@example.com").orElseThrow();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), "USER");

        CreateInquiryRequest request = new CreateInquiryRequest(
                "inquiry-user@example.com",
                InquiryCategory.ACCOUNT,
                "Account issue",
                "Please help with my account."
        );

        UUID inquiryId = UUID.fromString(given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/inquiries")
        .then()
                .statusCode(201)
                .body("email", equalTo("inquiry-user@example.com"))
                .body("category", equalTo("ACCOUNT"))
                .extract().path("inquiryId"));

        Inquiry savedInquiry = inquiryRepository.findById(inquiryId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(user.getId(), savedInquiry.getUser().getId());
    }

    @Test
    @DisplayName("get my inquiries")
    void getMyInquiries() {
        User user = userRepository.findByEmailAndDeletedAtIsNull("inquiry-user@example.com").orElseThrow();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), "USER");

        inquiryRepository.save(Inquiry.builder()
                .user(user)
                .email(user.getEmail())
                .category(InquiryCategory.BUG_REPORT)
                .title("Bug one")
                .content("First bug")
                .build());
        inquiryRepository.save(Inquiry.builder()
                .user(user)
                .email(user.getEmail())
                .category(InquiryCategory.FEATURE_REQUEST)
                .title("Feature one")
                .content("First feature")
                .build());

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/me/inquiries")
        .then()
                .statusCode(200)
                .body("inquiries", hasSize(2))
                .body("inquiries[0].email", equalTo("inquiry-user@example.com"));
    }
}
