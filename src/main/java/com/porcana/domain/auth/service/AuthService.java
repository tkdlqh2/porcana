package com.porcana.domain.auth.service;

import com.porcana.domain.arena.entity.ArenaSession;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.auth.command.LoginCommand;
import com.porcana.domain.auth.command.SignupCommand;
import com.porcana.domain.auth.dto.AuthResponse;
import com.porcana.domain.auth.oauth.OAuth2Provider;
import com.porcana.domain.auth.oauth.OAuth2ProviderFactory;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.user.dto.UserResponse;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import com.porcana.global.security.JwtTokenProvider;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PortfolioRepository portfolioRepository;
    private final ArenaSessionRepository arenaSessionRepository;
    private final OAuth2ProviderFactory oauth2ProviderFactory;

    @Transactional
    public AuthResponse signup(SignupCommand command) {
        if (userRepository.existsByEmail(command.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        String encodedPassword = passwordEncoder.encode(command.getPassword());
        User user = User.from(command, encodedPassword);

        User savedUser = userRepository.save(user);

        String accessToken = jwtTokenProvider.createAccessToken(savedUser.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(savedUser.getId());
        UserResponse userResponse = UserResponse.from(savedUser);

        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

    @Transactional
    public AuthResponse login(LoginCommand command) {
        User user;

        if (command.getProvider() == User.AuthProvider.EMAIL) {
            // EMAIL login
            user = loginWithEmail(command);
        } else {
            // OAuth login (GOOGLE, APPLE)
            user = loginWithOAuth(command);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        UserResponse userResponse = UserResponse.from(user);

        return new AuthResponse(accessToken, refreshToken, userResponse);
    }

    /**
     * Login with EMAIL provider
     */
    private User loginWithEmail(LoginCommand command) {
        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(command.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return user;
    }

    /**
     * Login with OAuth provider (GOOGLE, APPLE)
     * If user doesn't exist, create a new user automatically
     */
    private User loginWithOAuth(LoginCommand command) {
        // Verify OAuth code and get email
        OAuth2Provider provider = oauth2ProviderFactory.getProvider(command.getProvider());
        String email = provider.verifyAndGetEmail(command.getCode());

        log.info("OAuth login with provider {} for email {}", command.getProvider(), email);

        // Find existing user or create new one
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // Verify provider matches
            if (user.getProvider() != command.getProvider()) {
                throw new IllegalArgumentException(
                        "Email already registered with different provider: " + user.getProvider()
                );
            }

            log.info("Existing OAuth user found: {}", email);
            return user;
        }

        // Create new user for OAuth
        log.info("Creating new OAuth user for email: {}", email);

        User newUser = User.builder()
                .email(email)
                .password(null) // OAuth users don't have password
                .nickname(generateNicknameFromEmail(email))
                .provider(command.getProvider())
                .build();

        return userRepository.save(newUser);
    }

    /**
     * Generate nickname from email (use part before @)
     */
    private String generateNicknameFromEmail(String email) {
        String baseNickname = email.split("@")[0];

        // Check if nickname exists
        if (!userRepository.existsByNickname(baseNickname)) {
            return baseNickname;
        }

        // If exists, append random suffix
        String nickname;
        int suffix = 1;
        do {
            nickname = baseNickname + suffix;
            suffix++;
        } while (userRepository.existsByNickname(nickname));

        return nickname;
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken, UserResponse.from(user));
    }

    /**
     * Claim guest portfolios and arena sessions to user account
     * This should be called after signup or login if a guest session header exists
     *
     * @param guestSessionId Guest session ID from header
     * @param userId User ID to claim to
     */
    @Transactional
    public void claimGuestData(UUID guestSessionId, UUID userId) {
        if (guestSessionId == null) {
            log.debug("No guest session to claim for user {}", userId);
            return;
        }

        log.info("Claiming guest session {} to user {}", guestSessionId, userId);

        // 1) Claim guest portfolios with pessimistic lock
        List<Portfolio> guestPortfolios = portfolioRepository.findByGuestSessionIdForUpdate(guestSessionId);

        if (guestPortfolios.isEmpty()) {
            log.debug("No guest portfolios to claim for session {}", guestSessionId);
        } else {
            log.info("Found {} guest portfolios to claim", guestPortfolios.size());

            // Transfer ownership
            for (Portfolio portfolio : guestPortfolios) {
                portfolio.claimToUser(userId);
                log.debug("Claimed portfolio {} to user {}", portfolio.getId(), userId);
            }

            // Set main portfolio if user doesn't have one
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (user.getMainPortfolioId() == null && !guestPortfolios.isEmpty()) {
                UUID newestPortfolioId = guestPortfolios.stream()
                        .max(Comparator.comparing(Portfolio::getCreatedAt))
                        .get()
                        .getId();

                user.setMainPortfolioId(newestPortfolioId);
                log.info("Set main portfolio {} for user {}", newestPortfolioId, userId);
            }
        }

        // 2) Claim guest arena sessions with pessimistic lock
        List<ArenaSession> guestSessions = arenaSessionRepository.findByGuestSessionIdForUpdate(guestSessionId);

        if (!guestSessions.isEmpty()) {
            log.info("Found {} guest arena sessions to claim", guestSessions.size());

            for (ArenaSession session : guestSessions) {
                session.claimToUser(userId);
                log.debug("Claimed arena session {} to user {}", session.getId(), userId);
            }
        }

        log.info("Successfully claimed guest session {} to user {}", guestSessionId, userId);
    }
}
