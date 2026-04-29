package com.porcana.global.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:noreply@porcana.co.kr}")
    private String from;

    @Async
    public void sendVerificationEmail(String toEmail, UUID verificationToken) {
        String html = buildVerificationHtml(verificationToken);
        sendHtmlEmail(toEmail, "[Porcana] Email verification", html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, UUID resetToken) {
        String html = buildPasswordResetHtml(resetToken);
        sendHtmlEmail(toEmail, "[Porcana] Password reset", html);
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
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", maskEmail(to), subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", maskEmail(to), e.getMessage());
        }
    }

    private String buildVerificationHtml(UUID verificationToken) {
        String escapedToken = HtmlUtils.htmlEscape(verificationToken.toString());

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0f0f14;font-family:'Helvetica Neue',Arial,sans-serif">
                  <div style="max-width:560px;margin:0 auto;padding:48px 32px">
                    <div style="margin-bottom:40px">
                      <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px">Porcana</span>
                      <span style="color:#7b7b8a;font-size:13px;margin-left:10px">Email verification</span>
                    </div>
                    <h2 style="color:#ffffff;font-size:20px;font-weight:600;margin:0 0 12px">Verify your email</h2>
                    <p style="color:#b0b0c0;font-size:15px;line-height:1.7;margin:0 0 32px">
                      Use the token below with the backend verification API.<br>
                      No frontend or external app URL is required.
                    </p>
                    <div style="padding:18px 20px;background:#181824;border:1px solid #2c2c3a;border-radius:12px;margin-bottom:24px">
                      <p style="color:#7b7b8a;font-size:12px;text-transform:uppercase;letter-spacing:0.08em;margin:0 0 8px">Verification token</p>
                      <p style="color:#ffffff;font-size:18px;font-weight:700;word-break:break-all;margin:0">{{TOKEN}}</p>
                    </div>
                    <div style="padding:18px 20px;background:#14141d;border-radius:12px">
                      <p style="color:#d7d7e0;font-size:13px;line-height:1.7;margin:0">
                        GET <span style="color:#ffffff">/api/v1/auth/verify-email?token={{TOKEN}}</span>
                      </p>
                    </div>
                    <div style="margin-top:40px;padding-top:24px;border-top:1px solid #1e1e2a">
                      <p style="color:#5a5a6a;font-size:12px;line-height:1.6;margin:0">
                        This token expires in <strong style="color:#7b7b8a">24 hours</strong>.<br>
                        If you did not request this, you can ignore this email.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{TOKEN}}", escapedToken);
    }

    private String buildPasswordResetHtml(UUID resetToken) {
        String escapedToken = HtmlUtils.htmlEscape(resetToken.toString());

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0f0f14;font-family:'Helvetica Neue',Arial,sans-serif">
                  <div style="max-width:560px;margin:0 auto;padding:48px 32px">
                    <div style="margin-bottom:40px">
                      <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px">Porcana</span>
                      <span style="color:#7b7b8a;font-size:13px;margin-left:10px">Password reset</span>
                    </div>
                    <h2 style="color:#ffffff;font-size:20px;font-weight:600;margin:0 0 12px">Reset your password</h2>
                    <p style="color:#b0b0c0;font-size:15px;line-height:1.7;margin:0 0 32px">
                      Use the token below with the backend reset API.<br>
                      No frontend or external app URL is required.
                    </p>
                    <div style="padding:18px 20px;background:#181824;border:1px solid #2c2c3a;border-radius:12px;margin-bottom:24px">
                      <p style="color:#7b7b8a;font-size:12px;text-transform:uppercase;letter-spacing:0.08em;margin:0 0 8px">Reset token</p>
                      <p style="color:#ffffff;font-size:18px;font-weight:700;word-break:break-all;margin:0">{{TOKEN}}</p>
                    </div>
                    <div style="padding:18px 20px;background:#14141d;border-radius:12px">
                      <p style="color:#d7d7e0;font-size:13px;line-height:1.7;margin:0">
                        POST <span style="color:#ffffff">/api/v1/auth/reset-password</span><br>
                        JSON body: <span style="color:#ffffff">{ "token": "{{TOKEN}}", "newPassword": "..." }</span>
                      </p>
                    </div>
                    <div style="margin-top:40px;padding-top:24px;border-top:1px solid #1e1e2a">
                      <p style="color:#5a5a6a;font-size:12px;line-height:1.6;margin:0">
                        This token expires in <strong style="color:#7b7b8a">1 hour</strong> and can be used once.<br>
                        If you did not request this, you can ignore this email.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{TOKEN}}", escapedToken);
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
