package com.porcana.domain.inquiry;

import com.porcana.domain.inquiry.dto.CreateInquiryRequest;
import com.porcana.domain.inquiry.dto.InquiryItemResponse;
import com.porcana.domain.inquiry.dto.MyInquiryListResponse;
import com.porcana.domain.inquiry.service.InquiryService;
import com.porcana.global.guest.GuestSessionExtractor;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Inquiry", description = "Inquiry API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final GuestSessionExtractor guestSessionExtractor;

    @Operation(
            summary = "Create inquiry",
            description = "Create an inquiry as a guest or authenticated user."
    )
    @PostMapping("/inquiries")
    public ResponseEntity<InquiryItemResponse> createInquiry(
            @Valid @RequestBody CreateInquiryRequest request,
            HttpServletRequest httpRequest
    ) {
        InquiryItemResponse response = inquiryService.createInquiry(
                request,
                extractCurrentUserId(),
                guestSessionExtractor.extractGuestSessionId(httpRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get my inquiries",
            description = "Get inquiry history for the authenticated user.",
            security = {@SecurityRequirement(name = "JWT")}
    )
    @GetMapping("/me/inquiries")
    public ResponseEntity<MyInquiryListResponse> getMyInquiries(
            @CurrentUser UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        MyInquiryListResponse response = inquiryService.getMyInquiries(userId, pageable);
        return ResponseEntity.ok(response);
    }

    private UUID extractCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            return null;
        }
        return userId;
    }
}
