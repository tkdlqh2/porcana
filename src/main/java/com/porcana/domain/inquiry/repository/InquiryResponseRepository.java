package com.porcana.domain.inquiry.repository;

import com.porcana.domain.inquiry.entity.InquiryResponse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InquiryResponseRepository extends JpaRepository<InquiryResponse, UUID> {

    @EntityGraph(attributePaths = "responder")
    List<InquiryResponse> findByInquiryIdOrderBySentAtAsc(UUID inquiryId);
}
