package com.porcana.domain.inquiry.repository;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    Page<Inquiry> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Inquiry> findWithUserById(UUID inquiryId);

    @Query("""
            select i
            from Inquiry i
            left join i.user u
            where (:status is null or i.status = :status)
              and (
                    :keyword is null
                    or lower(i.title) like lower(concat('%', :keyword, '%'))
                    or lower(i.email) like lower(concat('%', :keyword, '%'))
                    or (u is not null and lower(u.nickname) like lower(concat('%', :keyword, '%')))
                  )
            """)
    Page<Inquiry> searchForAdmin(@Param("keyword") String keyword, @Param("status") InquiryStatus status, Pageable pageable);
}
