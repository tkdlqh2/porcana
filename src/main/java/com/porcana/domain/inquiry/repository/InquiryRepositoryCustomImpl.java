package com.porcana.domain.inquiry.repository;

import com.porcana.domain.inquiry.entity.Inquiry;
import com.porcana.domain.inquiry.entity.InquiryStatus;
import com.porcana.domain.inquiry.entity.QInquiry;
import com.porcana.domain.user.entity.QUser;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class InquiryRepositoryCustomImpl implements InquiryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Inquiry> searchForAdmin(String keyword, InquiryStatus status, Pageable pageable) {
        QInquiry inquiry = QInquiry.inquiry;
        QUser user = QUser.user;

        List<Inquiry> content = queryFactory
                .selectFrom(inquiry)
                .leftJoin(inquiry.user, user).fetchJoin()
                .where(
                        statusEq(status),
                        keywordContains(keyword)
                )
                .orderBy(inquiry.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(inquiry.count())
                .from(inquiry)
                .leftJoin(inquiry.user, user)
                .where(
                        statusEq(status),
                        keywordContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression statusEq(InquiryStatus status) {
        return status != null ? QInquiry.inquiry.status.eq(status) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String lower = keyword.toLowerCase();
        QInquiry inquiry = QInquiry.inquiry;
        QUser user = QUser.user;
        return inquiry.title.lower().contains(lower)
                .or(inquiry.email.lower().contains(lower))
                .or(user.nickname.lower().contains(lower));
    }
}