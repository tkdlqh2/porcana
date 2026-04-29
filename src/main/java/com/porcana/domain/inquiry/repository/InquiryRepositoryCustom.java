package com.porcana.domain.inquiry.repository;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepositoryCustom {

    Page<Inquiry> searchForAdmin(String keyword, InquiryStatus status, Pageable pageable);
}