package com.porcana.domain.user.repository;

import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * User Repository Custom Interface for QueryDSL
 */
public interface UserRepositoryCustom {

    /**
     * Search users by email or nickname (case-insensitive)
     */
    Page<User> searchByKeyword(String keyword, Pageable pageable);

    /**
     * Search users by email or nickname with role filter
     */
    Page<User> searchByKeywordAndRole(String keyword, UserRole role, Pageable pageable);
}
