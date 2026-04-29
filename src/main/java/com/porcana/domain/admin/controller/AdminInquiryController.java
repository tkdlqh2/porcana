package com.porcana.domain.admin.controller;

import com.porcana.domain.admin.dto.request.CreateInquiryResponseRequest;
import com.porcana.domain.admin.dto.request.UpdateInquiryStatusRequest;
import com.porcana.domain.admin.dto.response.AdminInquiryDetailResponse;
import com.porcana.domain.admin.dto.response.AdminInquiryListResponse;
import com.porcana.domain.admin.service.AdminInquiryService;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Inquiry", description = "Admin inquiry API")
@SecurityRequirement(name = "JWT")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @Operation(summary = "Get inquiries", description = "Get paginated inquiry list for admins.")
    @GetMapping
    public ResponseEntity<AdminInquiryListResponse> getInquiries(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Search by title, email, nickname") @RequestParam(required = false) String keyword,
            @Parameter(description = "Inquiry status filter") @RequestParam(required = false) InquiryStatus status
    ) {
        return ResponseEntity.ok(adminInquiryService.getInquiries(pageable, keyword, status));
    }

    @Operation(summary = "Get inquiry detail", description = "Get inquiry detail and response history.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<AdminInquiryDetailResponse> getInquiry(@PathVariable UUID inquiryId) {
        return ResponseEntity.ok(adminInquiryService.getInquiry(inquiryId));
    }

    @Operation(summary = "Respond to inquiry", description = "Add an admin response to an inquiry.")
    @PostMapping("/{inquiryId}/responses")
    public ResponseEntity<AdminInquiryDetailResponse> respondToInquiry(
            @PathVariable UUID inquiryId,
            @CurrentUser UUID adminUserId,
            @Valid @RequestBody CreateInquiryResponseRequest request
    ) {
        return ResponseEntity.ok(adminInquiryService.respondToInquiry(inquiryId, adminUserId, request));
    }

    @Operation(summary = "Update inquiry status", description = "Update inquiry status.")
    @PatchMapping("/{inquiryId}/status")
    public ResponseEntity<AdminInquiryDetailResponse> updateStatus(
            @PathVariable UUID inquiryId,
            @Valid @RequestBody UpdateInquiryStatusRequest request
    ) {
        return ResponseEntity.ok(adminInquiryService.updateStatus(inquiryId, request.status()));
    }
}
