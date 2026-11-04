package com.campuslink.module.account.infrastructure.notify;

import com.campuslink.module.account.domain.gateway.CodeSender;
import com.campuslink.module.account.domain.gateway.CodeDeliveryException;
import com.campuslink.config.AppProperties;
import org.springframework.mail.MailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 策略实现（生产）：SMTP 邮件发送 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campuslink.code-sender.mode", havingValue = "mail")
public class MailCodeSender implements CodeSender {

    private final JavaMailSender mailSender;
    private final AppProperties props;

    @Override
    public void send(String target, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.getCodeSender().getFrom());
        message.setTo(target);
        message.setSubject("Campus-Link 验证码");
        message.setText("你的验证码是：" + code + "，" + props.getCaptcha().getTtlMinutes()
                + " 分钟内有效。若非本人操作请忽略本邮件。");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new CodeDeliveryException();
        }
        log.info("Captcha mail accepted operationId={}", com.campuslink.common.result.TraceIds.newTraceId());
    }
}
