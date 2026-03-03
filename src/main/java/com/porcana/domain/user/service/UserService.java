package com.porcana.domain.user.service;

import com.porcana.domain.arena.service.ArenaService;
import com.porcana.domain.portfolio.service.PortfolioService;
import com.porcana.domain.user.command.UpdateUserCommand;
import com.porcana.domain.user.dto.UserResponse;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PortfolioService portfolioService;
    private final ArenaService arenaService;
    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateUserCommand command) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.updateNickname(command.getNickname());

        return UserResponse.from(user);
    }

    @Transactional
    public void deleteMe(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 회원의 아레나 세션 hard delete (ArenaRound 포함)
        arenaService.deleteAllSessionsForUser(userId);

        // 회원의 포트폴리오 soft delete
        portfolioService.deleteAllPortfoliosForUser(userId);

        user.delete();
    }
}
