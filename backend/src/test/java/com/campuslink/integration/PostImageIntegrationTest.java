package com.campuslink.integration;
import com.campuslink.common.markdown.PostImageUrls;
import com.campuslink.module.forum.application.PostImageApplicationService;
import com.campuslink.module.forum.domain.gateway.PostImageRepository;
import com.campuslink.module.forum.domain.model.PostImage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
class PostImageIntegrationTest {
    @Autowired PostImageRepository repository;
    @Autowired PostImageApplicationService images;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Test void bindingAndBusinessRollbackTogether() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            repository.reserve(new PostImage(id, 1, null, false, "UPLOADING", id + ".png", "image/png", 1, 1, 1, Instant.now()));
            repository.state(id, "READY");
            images.bind(99195, 1, "<img src=\"" + PostImageUrls.url(id) + "\">");
            assertThat(repository.find(id).orElseThrow().postId()).isEqualTo(99195);
            tx.setRollbackOnly();
        });
        assertThat(repository.find(id)).isEmpty();
    }
    @Test void concurrentPostsCannotStealOneImage() throws Exception {
        UUID id = UUID.randomUUID();
        repository.reserve(new PostImage(id, 1, null, false, "UPLOADING", id + ".png", "image/png", 1, 1, 1, Instant.now()));
        repository.state(id, "READY");
        var ready = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        Runnable bind = () -> { try {
            ready.await(); images.bind(9_000_000_000L + Thread.currentThread().threadId(), 1,
                    "<img src=\"" + PostImageUrls.url(id) + "\">"); results.add(true);
        } catch (com.campuslink.common.exception.ApiException ex) { results.add(false); }
          catch (InterruptedException ex) { Thread.currentThread().interrupt(); } };
        Thread one = new Thread(bind); Thread two = new Thread(bind);
        one.start(); two.start(); ready.countDown(); one.join(10000); two.join(10000);
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(repository.find(id).orElseThrow().inUse()).isTrue();
    }
}
