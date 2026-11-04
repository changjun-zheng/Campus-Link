package com.campuslink.module.account.infrastructure.notify;

import com.campuslink.config.AppProperties;
import com.campuslink.module.account.domain.gateway.CodeDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A loopback SMTP fixture exercises the real mail library; it never sends external mail. */
class MailCodeSenderSmtpTest {
    @Test
    void acceptedMessageContainsConfiguredSenderAndRandomCode() throws Exception {
        try (var smtp = new SmtpFixture(false, false)) {
            var sender = sender(smtp);
            sender.send("recipient@example.invalid", "736245");
            var mime = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(smtp.message.getBytes(StandardCharsets.UTF_8)));
            assertThat(mime.getFrom()[0].toString()).isEqualTo("sender@example.invalid");
            assertThat(mime.getContent().toString()).contains("736245", "5 分钟");
        }
    }

    @Test
    void authenticationFailureIsSanitized() throws Exception {
        try (var smtp = new SmtpFixture(true, false)) {
            assertThatThrownBy(() -> sender(smtp).send("recipient@example.invalid", "736245"))
                    .isInstanceOf(CodeDeliveryException.class).hasNoCause();
            assertThat(smtp.message).isEmpty();
        }
    }

    @Test
    void serverReadTimeoutIsSanitized() throws Exception {
        try (var smtp = new SmtpFixture(false, true)) {
            assertThatThrownBy(() -> sender(smtp).send("recipient@example.invalid", "736245"))
                    .isInstanceOf(CodeDeliveryException.class).hasNoCause();
        }
    }

    @Test
    void requiredStarttlsNeverSendsToPlaintextServer() throws Exception {
        try (var smtp = new SmtpFixture(false, false)) {
            var mail = transport(smtp);
            mail.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
            mail.getJavaMailProperties().setProperty("mail.smtp.starttls.required", "true");
            assertThatThrownBy(() -> new MailCodeSender(mail, properties())
                    .send("recipient@example.invalid", "736245"))
                    .isInstanceOf(CodeDeliveryException.class);
            assertThat(smtp.message).isEmpty();
        }
    }

    private static AppProperties properties() {
        var props = new AppProperties();
        props.getCodeSender().setFrom("sender@example.invalid");
        return props;
    }

    private static MailCodeSender sender(SmtpFixture smtp) {
        return new MailCodeSender(transport(smtp), properties());
    }

    private static JavaMailSenderImpl transport(SmtpFixture smtp) {
        var mail = new JavaMailSenderImpl();
        mail.setHost("127.0.0.1");
        mail.setPort(smtp.server.getLocalPort());
        mail.setDefaultEncoding("UTF-8");
        mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "200");
        mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "200");
        mail.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "200");
        if (smtp.rejectAuth) {
            mail.setUsername(java.util.UUID.randomUUID().toString());
            mail.setPassword(java.util.UUID.randomUUID().toString());
            mail.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        }
        return mail;
    }

    private static final class SmtpFixture implements AutoCloseable {
        private final ServerSocket server;
        private final boolean rejectAuth;
        private volatile String message = "";
        private final Thread worker;

        private SmtpFixture(boolean rejectAuth, boolean stall) throws IOException {
            this.rejectAuth = rejectAuth;
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            worker = Thread.ofPlatform().daemon().start(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(1500);
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    if (stall) {
                        Thread.sleep(450);
                        return;
                    }
                    out.print("220 fixture\r\n"); out.flush();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("EHLO")) {
                            out.print(rejectAuth ? "250-fixture\r\n250 AUTH LOGIN\r\n" : "250 fixture\r\n");
                        } else if (line.startsWith("AUTH")) {
                            out.print("535 Authentication failed\r\n");
                        } else if (line.equals("DATA")) {
                            out.print("354 End with dot\r\n"); out.flush();
                            StringBuilder body = new StringBuilder();
                            while ((line = in.readLine()) != null && !line.equals(".")) {
                                body.append(line).append("\r\n");
                            }
                            message = body.toString();
                            out.print("250 Accepted\r\n");
                        } else if (line.equals("QUIT")) {
                            out.print("221 Bye\r\n"); out.flush(); break;
                        } else {
                            out.print("250 OK\r\n");
                        }
                        out.flush();
                    }
                } catch (IOException | InterruptedException ignored) {
                    // Client aborts are expected for timeout and mandatory TLS failure probes.
                }
            });
        }

        @Override
        public void close() throws Exception {
            server.close();
            worker.join(2000);
        }
    }
}
