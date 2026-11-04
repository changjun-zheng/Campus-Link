package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.PostImageUrls;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.*;
import com.campuslink.module.forum.domain.model.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PostImageApplicationServiceTest {
    private final PostImageRepository repo = mock(PostImageRepository.class);
    private final PostImageStorage storage = mock(PostImageStorage.class);
    private final PostImageRateLimit rate = mock(PostImageRateLimit.class);
    private final AccountApplicationService accounts = mock(AccountApplicationService.class);
    private final ForumQueryApplicationService forum = mock(ForumQueryApplicationService.class);
    private final AppProperties props = new AppProperties();
    private PostImageApplicationService service;
    private final UUID id = UUID.randomUUID();
    private PostImage image(Long postId, boolean used, long owner) {
        return new PostImage(id, owner, postId, used, "READY", id + ".png", "image/png", 4, 2, 2, Instant.now());
    }
    private String html() { return "<img src=\"" + PostImageUrls.url(id) + "\">"; }
    @BeforeEach void setUp() { service = new PostImageApplicationService(repo, storage, rate, accounts, forum, props); }
    @Test void draftOnlyOwnerCanRead() {
        when(repo.find(id)).thenReturn(Optional.of(image(null, false, 1)));
        when(storage.read(anyString())).thenReturn(new byte[]{1});
        assertThatThrownBy(() -> service.read(id, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read(id, 2L)).isInstanceOf(ApiException.class);
        assertThat(service.read(id, 1L).bytes()).containsExactly((byte) 1);
    }
    @Test void boundHiddenPostIsCheckedEvenForOwnerAndRemovedReference() {
        when(repo.find(id)).thenReturn(Optional.of(image(7L, false, 1)));
        doThrow(new ApiException(com.campuslink.common.result.ResultCode.NOT_FOUND)).when(forum).requireReadablePost(7L, 1L);
        assertThatThrownBy(() -> service.read(id, 1L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(storage);
    }
    @Test void publicReferenceSupportsAnonymousAndRemovedReferenceDoesNot() {
        when(repo.find(id)).thenReturn(Optional.of(image(7L, true, 1)));
        when(storage.read(anyString())).thenReturn(new byte[]{2});
        assertThat(service.read(id, null).bytes()).containsExactly((byte) 2);
        verify(forum).requireReadablePost(7L, null);
        when(repo.find(id)).thenReturn(Optional.of(image(7L, false, 1)));
        assertThatThrownBy(() -> service.read(id, null)).isInstanceOf(ApiException.class);
    }
    @Test void wrongOwnerAndCrossPostFailBeforeAnyBindingWrite() {
        when(repo.lock(id)).thenReturn(Optional.of(image(null, false, 2)));
        assertThatThrownBy(() -> service.bind(7, 1, html())).isInstanceOf(ApiException.class);
        when(repo.lock(id)).thenReturn(Optional.of(image(8L, true, 1)));
        assertThatThrownBy(() -> service.bind(7, 1, html())).isInstanceOf(ApiException.class);
        verify(repo, never()).reference(any(), anyLong());
        verify(repo, never()).clearReferences(anyLong());
    }
    @Test void rollbackSwitchAllowsExistingButRejectsNewBinding() {
        props.getPostImages().setUploadEnabled(false);
        when(repo.lock(id)).thenReturn(Optional.of(image(7L, false, 1)));
        service.bind(7, 1, html());
        verify(repo).reference(id, 7);
        when(repo.lock(id)).thenReturn(Optional.of(image(null, false, 1)));
        assertThatThrownBy(() -> service.bind(7, 1, html())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.upload(1, new byte[]{1})).isInstanceOf(ApiException.class);
    }
    @Test void storageFailureRetainsFailedReservation() {
        when(rate.allow(1)).thenReturn(true);
        when(storage.prepare(any())).thenReturn(new EncodedPostImage(new byte[]{1}, "image/png", 1, 1));
        doThrow(new IllegalStateException()).when(storage).store(anyString(), any());
        assertThatThrownBy(() -> service.upload(1, new byte[]{1})).isInstanceOf(ApiException.class);
        verify(repo).reserve(any());
        verify(repo).state(any(), eq("FAILED"));
        verify(repo, never()).state(any(), eq("READY"));
    }
    @Test void rateLimitAndDisabledReadDoNotTouchStorage() {
        assertThatThrownBy(() -> service.upload(1, new byte[]{1})).isInstanceOf(ApiException.class);
        props.getPostImages().setReadEnabled(false);
        assertThatThrownBy(() -> service.read(id, 1L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(storage);
    }
    @Test void uploadRegistersThenWritesThenMarksReady() {
        when(rate.allow(1)).thenReturn(true);
        when(storage.prepare(any())).thenReturn(new EncodedPostImage(new byte[]{1}, "image/png", 1, 1));
        var result = service.upload(1, new byte[]{1});
        var order = inOrder(repo, storage);
        order.verify(storage).prepare(any()); order.verify(repo).reserve(any());
        order.verify(storage).store(anyString(), any()); order.verify(repo).state(result.id(), "READY");
        assertThat(result.url()).isEqualTo(PostImageUrls.url(result.id()));
    }
    @Test void tooManyDistinctImagesRejectedAndCodeExampleIgnored() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 11; i++) html.append("<img src=\"").append(PostImageUrls.url(UUID.randomUUID())).append("\">");
        assertThatThrownBy(() -> service.bind(7, 1, html.toString())).isInstanceOf(ApiException.class);
        service.bind(7, 1, "<pre><code>&lt;img src=\"" + PostImageUrls.url(id) + "\"&gt;</code></pre>");
        verify(repo).clearReferences(7);
        verify(repo, never()).lock(any());
    }
}
