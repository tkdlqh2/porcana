package com.porcana.domain.user.repository;

import com.porcana.domain.user.entity.QUser;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * User Repository Custom Implementation using QueryDSL
 */
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchByKeyword(String keyword, Pageable pageable) {
        QUser user = QUser.user;

        List<User> content = queryFactory
                .selectFrom(user)
                .where(
                        deletedAtIsNull(),
                        keywordContains(keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(user.createdAt.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        deletedAtIsNull(),
                        keywordContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<User> searchByKeywordAndRole(String keyword, UserRole role, Pageable pageable) {
        QUser user = QUser.user;

        List<User> content = queryFactory
                .selectFrom(user)
                .where(
                        deletedAtIsNull(),
                        roleEq(role),
                        keywordContains(keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(user.createdAt.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(user.count())
                .from(user)
                .where(
                        deletedAtIsNull(),
                        roleEq(role),
                        keywordContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression deletedAtIsNull() {
        return QUser.user.deletedAt.isNull();
    }

    private BooleanExpression roleEq(UserRole role) {
        return role != null ? QUser.user.role.eq(role) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String lowerKeyword = keyword.toLowerCase();
        return QUser.user.email.lower().contains(lowerKeyword)
                .or(QUser.user.nickname.lower().contains(lowerKeyword));
    }
}
