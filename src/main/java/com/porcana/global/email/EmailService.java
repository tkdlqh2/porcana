package com.porcana.global.email;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String SENDER_NAME = "Porcana";
    private static final String TOKEN_PLACEHOLDER = "{{TOKEN}}";
    private static final String VERIFICATION_TEMPLATE_PATH = "templates/email/verification.html";
    private static final String PASSWORD_RESET_TEMPLATE_PATH = "templates/email/password-reset.html";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:noreply@porcana.co.kr}")
    private String from;

    private String verificationTemplate;
    private String passwordResetTemplate;

    @PostConstruct
    void loadTemplates() {
        this.verificationTemplate = loadTemplate(VERIFICATION_TEMPLATE_PATH);
        this.passwordResetTemplate = loadTemplate(PASSWORD_RESET_TEMPLATE_PATH);
    }

    @Async
    public void sendVerificationEmail(String toEmail, String verificationToken) {
        String html = verificationTemplate.replace(TOKEN_PLACEHOLDER, HtmlUtils.htmlEscape(verificationToken));
        sendHtmlEmail(toEmail, "[Porcana] 이메일 인증 안내", html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String html = passwordResetTemplate.replace(TOKEN_PLACEHOLDER, HtmlUtils.htmlEscape(resetToken));
        sendHtmlEmail(toEmail, "[Porcana] 비밀번호 재설정 안내", html);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender not configured. Skipping email to {}: {}", maskEmail(to), subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", maskEmail(to), subject);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", maskEmail(to), subject, e);
        }
    }

    private String loadTemplate(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load email template: " + path, e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return localPart.charAt(0) + "***" + domain;
    }
}