package com.porcana.domain.admin.controller;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryCategory;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.domain.inquiry.repository.InquiryRepository;
import com.porcana.domain.inquiry.repository.InquiryResponseRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
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

class AdminInquiryControllerTest extends BaseIntegrationTest {

    private static final String ADMIN_BASE_PATH = "/api/v1/admin";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryResponseRepository inquiryResponseRepository;

    private UUID adminId;
    private UUID userId;
    private UUID inquiryId;

    @BeforeEach
    void setUp() {
        inquiryResponseRepository.deleteAll();
        inquiryRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
                .email("admin-inquiry@example.com")
                .password("password123")
                .nickname("admin-user")
                .provider(User.AuthProvider.EMAIL)
                .role(UserRole.ADMIN)
                .build());
        User user = userRepository.save(User.builder()
                .email("member-inquiry@example.com")
                .password("password123")
                .nickname("member-user")
                .provider(User.AuthProvider.EMAIL)
                .build());

        adminId = admin.getId();
        userId = user.getId();

        Inquiry inquiry = inquiryRepository.save(Inquiry.builder()
                .user(user)
                .email(user.getEmail())
                .category(InquiryCategory.GENERAL)
                .title("Need admin response")
                .content("Please respond")
                .build());
        inquiryId = inquiry.getId();
    }

    private String createAdminAccessToken() {
        return jwtTokenProvider.createAccessToken(adminId, "ADMIN");
    }

    @Test
    @DisplayName("admin can get inquiry list")
    void getInquiries() {
        given()
                .basePath(ADMIN_BASE_PATH)
                .header("Authorization", "Bearer " + createAdminAccessToken())
        .when()
                .get("/inquiries")
        .then()
                .statusCode(200)
                .body("inquiries", hasSize(1))
                .body("inquiries[0].inquiryId", equalTo(inquiryId.toString()))
                .body("inquiries[0].userId", equalTo(userId.toString()));
    }

    @Test
    @DisplayName("admin can respond to inquiry")
    void respondToInquiry() {
        given()
                .basePath(ADMIN_BASE_PATH)
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "content": "We are looking into it."
                        }
                        """)
        .when()
                .post("/inquiries/{inquiryId}/responses", inquiryId)
        .then()
                .statusCode(201)
                .body("inquiryId", equalTo(inquiryId.toString()))
                .body("status", equalTo("IN_PROGRESS"))
                .body("responses", hasSize(1))
                .body("responses[0].responderId", equalTo(adminId.toString()))
                .body("responses[0].content", equalTo("We are looking into it."));

        var storedResponses = inquiryResponseRepository.findByInquiryIdOrderBySentAtAsc(inquiryId);
        org.junit.jupiter.api.Assertions.assertEquals(1, storedResponses.size());
        org.junit.jupiter.api.Assertions.assertEquals(adminId, storedResponses.get(0).getResponder().getId());
        org.junit.jupiter.api.Assertions.assertEquals("We are looking into it.", storedResponses.get(0).getContent());
    }

    @Test
    @DisplayName("admin can update inquiry status")
    void updateInquiryStatus() {
        given()
                .basePath(ADMIN_BASE_PATH)
                .header("Authorization", "Bearer " + createAdminAccessToken())
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "status": "RESOLVED"
                        }
                        """)
        .when()
                .patch("/inquiries/{inquiryId}/status", inquiryId)
        .then()
                .statusCode(200)
                .body("inquiryId", equalTo(inquiryId.toString()))
                .body("status", equalTo("RESOLVED"));

        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(InquiryStatus.RESOLVED, inquiry.getStatus());
    }
}
