package com.campuslink.module.account.infrastructure.notify;

import com.campuslink.config.AppProperties;
import com.campuslink.module.account.domain.gateway.CodeDeliveryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class MailCodeSenderTest {
    @Test
    void setsFromAndConfiguredLifetimeWithoutLoggingRecipientOrCode(CapturedOutput output) {
        var mail = mock(JavaMailSender.class);
        var props = new AppProperties();
        props.getCodeSender().setFrom("sender@example.invalid");
        props.getCaptcha().setTtlMinutes(7);
        new MailCodeSender(mail, props).send("recipient@example.invalid", "384729");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.invalid");
        assertThat(message.getValue().getTo()).containsExactly("recipient@example.invalid");
        assertThat(message.getValue().getText()).contains("384729", "7 分钟");
        assertThat(output.getAll()).doesNotContain("recipient@example.invalid", "384729");
    }

    @Test
    void providerFailureDoesNotCarryProviderDetails(CapturedOutput output) {
        var mail = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("private provider credential and recipient"))
                .when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new MailCodeSender(mail, new AppProperties())
                .send("recipient@example.invalid", "384729"))
                .isInstanceOf(CodeDeliveryException.class).hasNoCause()
                .hasMessage("Code delivery unavailable");
        assertThat(output.getAll()).doesNotContain("private provider", "recipient@example.invalid", "384729");
    }
}
