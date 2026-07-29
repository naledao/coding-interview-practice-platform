package xyz.kangnasi.interview.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.kangnasi.interview.common.AppException;

@Component
public class MailLoginCodeSender {

    private static final Logger log = LoggerFactory.getLogger(MailLoginCodeSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public MailLoginCodeSender(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:${spring.mail.username:}}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String code, int ttlMinutes) {
        if (from == null || from.isBlank()) {
            throw AppException.badRequest("邮件发送账号未配置");
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw AppException.badRequest("邮件发送服务未配置");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("登录验证码");
        message.setText("您的登录验证码为：" + code + "，" + ttlMinutes + "分钟内有效。");

        try {
            sender.send(message);
            log.info("Sent login code email to {}", email);
        } catch (MailException exception) {
            log.warn("Failed to send login code email to {}", email, exception);
            throw AppException.badRequest("验证码发送失败");
        }
    }
}
