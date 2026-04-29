package com.porcana.global.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Async
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        String html = buildVerificationHtml(verificationUrl);
        sendHtmlEmail(toEmail, "[Porcana] 이메일 인증을 완료해주세요", html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        String html = buildPasswordResetHtml(resetUrl);
        sendHtmlEmail(toEmail, "[Porcana] 비밀번호 재설정 링크", html);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildVerificationHtml(String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0f0f14;font-family:'Helvetica Neue',Arial,sans-serif">
                  <div style="max-width:560px;margin:0 auto;padding:48px 32px">
                    <div style="margin-bottom:40px">
                      <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px">Porcana</span>
                      <span style="color:#7b7b8a;font-size:13px;margin-left:10px">내 손안의 투자 포트폴리오</span>
                    </div>
                    <h2 style="color:#ffffff;font-size:20px;font-weight:600;margin:0 0 12px">이메일 인증</h2>
                    <p style="color:#b0b0c0;font-size:15px;line-height:1.7;margin:0 0 32px">
                      Porcana에 가입해주셔서 감사합니다.<br>
                      아래 버튼을 눌러 이메일 인증을 완료해주세요.
                    </p>
                    <a href="{{VERIFICATION_URL}}"
                       style="display:inline-block;padding:14px 28px;background:#6c63ff;color:#ffffff;text-decoration:none;border-radius:8px;font-size:15px;font-weight:600">
                      이메일 인증하기
                    </a>
                    <div style="margin-top:40px;padding-top:24px;border-top:1px solid #1e1e2a">
                      <p style="color:#5a5a6a;font-size:12px;line-height:1.6;margin:0">
                        이 링크는 <strong style="color:#7b7b8a">24시간</strong> 후 만료됩니다.<br>
                        본인이 요청하지 않은 경우 이 이메일을 무시하세요.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{VERIFICATION_URL}}", verificationUrl);
    }

    private String buildPasswordResetHtml(String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#0f0f14;font-family:'Helvetica Neue',Arial,sans-serif">
                  <div style="max-width:560px;margin:0 auto;padding:48px 32px">
                    <div style="margin-bottom:40px">
                      <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:-0.5px">Porcana</span>
                      <span style="color:#7b7b8a;font-size:13px;margin-left:10px">내 손안의 투자 포트폴리오</span>
                    </div>
                    <h2 style="color:#ffffff;font-size:20px;font-weight:600;margin:0 0 12px">비밀번호 재설정</h2>
                    <p style="color:#b0b0c0;font-size:15px;line-height:1.7;margin:0 0 32px">
                      비밀번호 재설정을 요청하셨습니다.<br>
                      아래 버튼을 눌러 새 비밀번호를 설정해주세요.
                    </p>
                    <a href="{{RESET_URL}}"
                       style="display:inline-block;padding:14px 28px;background:#6c63ff;color:#ffffff;text-decoration:none;border-radius:8px;font-size:15px;font-weight:600">
                      비밀번호 재설정하기
                    </a>
                    <div style="margin-top:40px;padding-top:24px;border-top:1px solid #1e1e2a">
                      <p style="color:#5a5a6a;font-size:12px;line-height:1.6;margin:0">
                        이 링크는 <strong style="color:#7b7b8a">1시간</strong> 후 만료되며 1회만 사용 가능합니다.<br>
                        본인이 요청하지 않은 경우 이 이메일을 무시하세요.<br>
                        계정 보안을 위해 타인과 공유하지 마세요.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{RESET_URL}}", resetUrl);
    }
}