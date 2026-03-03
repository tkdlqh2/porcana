package com.porcana.domain.user.repository;

import com.porcana.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
