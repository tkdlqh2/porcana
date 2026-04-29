package com.porcana.domain.inquiry.service;

import com.porcana.domain.inquiry.dto.CreateInquiryRequest;
import com.porcana.domain.inquiry.dto.InquiryItemResponse;
import com.porcana.domain.inquiry.dto.MyInquiryListResponse;
import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.repository.InquiryRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    @Transactional
    public InquiryItemResponse createInquiry(CreateInquiryRequest request, UUID userId, UUID guestSessionId) {
        User user = userId == null ? null : userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .guestSessionId(guestSessionId)
                .email(request.email())
                .category(request.category())
                .title(request.title())
                .content(request.content())
                .build();

        Inquiry savedInquiry = inquiryRepository.save(inquiry);
        log.info("Inquiry created: inquiryId={}, userId={}, guestSessionId={}",
                savedInquiry.getId(), userId, guestSessionId);

        return InquiryItemResponse.from(savedInquiry);
    }

    @Transactional(readOnly = true)
    public MyInquiryListResponse getMyInquiries(UUID userId, Pageable pageable) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Page<Inquiry> inquiries = inquiryRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return MyInquiryListResponse.from(inquiries);
    }
}
