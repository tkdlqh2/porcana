package com.porcana.domain.inquiry.repository;

import com.porcana.domain.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID>, InquiryRepositoryCustom {

    Page<Inquiry> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Inquiry> findWithUserById(UUID inquiryId);
}
