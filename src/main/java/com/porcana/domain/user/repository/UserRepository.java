package com.porcana.domain.user.repository;

import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    // Admin API methods
    Page<User> findByDeletedAtIsNull(Pageable pageable);

    Page<User> findByRoleAndDeletedAtIsNull(UserRole role, Pageable pageable);

    boolean existsByRoleAndDeletedAtIsNull(UserRole role);
}
