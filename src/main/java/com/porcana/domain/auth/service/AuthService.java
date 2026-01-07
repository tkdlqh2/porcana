package com.porcana.domain.auth.service;

import com.porcana.domain.auth.command.LoginCommand;
import com.porcana.domain.auth.command.SignupCommand;
import com.porcana.domain.auth.dto.AuthResponse;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import com.porcana.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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

        return new AuthResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginCommand command) {
        // EMAIL provider only for now
        if (command.getProvider() != User.AuthProvider.EMAIL) {
            throw new UnsupportedOperationException("Only EMAIL provider is supported currently");
        }

        User user = userRepository.findByEmail(command.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(command.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        return new AuthResponse(accessToken, refreshToken);
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

        return new AuthResponse(newAccessToken, newRefreshToken);
    }
}