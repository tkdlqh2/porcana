package com.porcana.global.config;

import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Initialize admin user on application startup from environment variables.
 * Only creates admin if:
 * 1. Environment variables are set (ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME)
 * 2. No admin user exists in the database
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${admin.nickname:Admin}")
    private String adminNickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Skip if environment variables are not set
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.debug("Admin initialization skipped: ADMIN_EMAIL or ADMIN_PASSWORD not set");
            return;
        }

        // Skip if admin already exists
        if (userRepository.existsByRoleAndDeletedAtIsNull(UserRole.ADMIN)) {
            log.debug("Admin initialization skipped: Admin user already exists");
            return;
        }

        // Check if email is already used
        if (userRepository.existsByEmail(adminEmail)) {
            log.warn("Admin initialization skipped: Email {} is already registered", adminEmail);
            return;
        }

        // Create admin user
        User admin = User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .nickname(adminNickname)
                .provider(User.AuthProvider.EMAIL)
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Admin user created successfully: {}", adminEmail);
    }
}