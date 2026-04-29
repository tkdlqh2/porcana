package com.porcana.domain.admin.service;

import com.porcana.domain.admin.dto.request.CreateInquiryResponseRequest;
import com.porcana.domain.admin.dto.response.AdminInquiryDetailResponse;
import com.porcana.domain.admin.dto.response.AdminInquiryListResponse;
import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryResponse;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.domain.inquiry.repository.InquiryRepository;
import com.porcana.domain.inquiry.repository.InquiryResponseRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryResponseRepository inquiryResponseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminInquiryListResponse getInquiries(Pageable pageable, String keyword, InquiryStatus status) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return AdminInquiryListResponse.from(inquiryRepository.searchForAdmin(normalizedKeyword, status, pageable));
    }

    @Transactional(readOnly = true)
    public AdminInquiryDetailResponse getInquiry(UUID inquiryId) {
        Inquiry inquiry = inquiryRepository.findWithUserById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("Inquiry not found: " + inquiryId));

        List<InquiryResponse> responses = inquiryResponseRepository.findByInquiryIdOrderBySentAtAsc(inquiryId);
        return AdminInquiryDetailResponse.from(inquiry, responses);
    }

    @Transactional
    public AdminInquiryDetailResponse respondToInquiry(UUID inquiryId, UUID adminUserId, CreateInquiryResponseRequest request) {
        Inquiry inquiry = inquiryRepository.findWithUserById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("Inquiry not found: " + inquiryId));
        User responder = userRepository.findByIdAndDeletedAtIsNull(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        LocalDateTime sentAt = LocalDateTime.now();
        InquiryResponse response = InquiryResponse.builder()
                .inquiry(inquiry)
                .responder(responder)
                .content(request.content())
                .sentAt(sentAt)
                .build();
        inquiryResponseRepository.save(response);
        inquiry.markResponded(sentAt);

        log.info("Inquiry response created: inquiryId={}, responderId={}", inquiryId, adminUserId);
        return getInquiry(inquiryId);
    }

    @Transactional
    public AdminInquiryDetailResponse updateStatus(UUID inquiryId, InquiryStatus status) {
        Inquiry inquiry = inquiryRepository.findWithUserById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("Inquiry not found: " + inquiryId));
        inquiry.updateStatus(status);
        log.info("Inquiry status updated: inquiryId={}, status={}", inquiryId, status);
        return getInquiry(inquiryId);
    }
}
