package com.campuslink.module.forum.application;

import com.campuslink.common.audit.AuditService;
import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端标签用例（F-ADMIN-004 标签侧 / CR-132）。
 *
 * <p>守四条：① 分页归一（page&lt;1 / size 越界）与 offset 计算；② 标签不存在 → 3001 且
 * **不删数据、不记审计**；③ 删除成功才记 {@code TAG_DELETE}，detail 带标签名与受影响帖子数；
 * ④ 出参按仓储顺序组装、字段不串位。
 */
@ExtendWith(MockitoExtension.class)
class AdminTagApplicationServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long TAG_ID = 8L;

    @Mock
    private TagRepository tagRepository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private AdminTagApplicationService service;

    @Test
    @DisplayName("标签列表：分页归一（size 999 → 100、page 0 → 1），条目按仓储顺序映射")
    void pageNormalizesAndMapsItems() {
        when(tagRepository.countTags("java")).thenReturn(1L);
        when(tagRepository.pageTags("java", "name", 0, 100)).thenReturn(List.of(
                new TagRepository.TagSummary(TAG_ID, "java", 7, Instant.parse("2026-10-20T10:00:00Z"))));

        AdminTagApplicationService.AdminTags result = service.page("java", "name", 0, 999);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(TAG_ID);
        assertThat(result.items().get(0).name()).isEqualTo("java");
        assertThat(result.items().get(0).useCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("删除：标签不存在 → 404 / 3001，不删数据、不记审计")
    void deleteMissingTagIsNotFound() {
        when(tagRepository.findTag(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OPERATOR_ID, 9L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(tagRepository, never()).deleteTag(anyLong());
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("删除成功：调用仓储并记 TAG_DELETE 审计（detail 含标签名与受影响帖子数）")
    void deleteRecordsAuditWithAffectedPosts() {
        when(tagRepository.findTag(TAG_ID))
                .thenReturn(Optional.of(new TagRepository.TagSummary(TAG_ID, "java", 7, Instant.now())));
        when(tagRepository.deleteTag(TAG_ID)).thenReturn(2);

        service.delete(OPERATOR_ID, TAG_ID);

        verify(tagRepository).deleteTag(TAG_ID);
        verify(auditService).record(OPERATOR_ID, "TAG_DELETE", "tags", TAG_ID, "tagName=java, affectedPosts=2");
    }

    @Test
    @DisplayName("分页归一：page 3 / size 10 → offset 20")
    void pageComputesOffset() {
        when(tagRepository.countTags(null)).thenReturn(0L);
        when(tagRepository.pageTags(null, "use_count", 20, 10)).thenReturn(List.of());

        AdminTagApplicationService.AdminTags result = service.page(null, "use_count", 3, 10);

        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.items()).isEmpty();
        verify(tagRepository).pageTags(null, "use_count", 20, 10);
    }

    @Test
    @DisplayName("重命名：去首尾空白后写库，记 TAG_RENAME 审计（detail 含旧名 -> 新名与受影响帖子数）")
    void renameTrimsAndRecordsAudit() {
        when(tagRepository.findTag(TAG_ID))
                .thenReturn(Optional.of(new TagRepository.TagSummary(TAG_ID, "java", 1, Instant.now())));
        when(tagRepository.findTagByName("JVM")).thenReturn(Optional.empty());
        when(tagRepository.renameTag(TAG_ID, "JVM")).thenReturn(3);

        service.rename(OPERATOR_ID, TAG_ID, "  JVM  ");

        verify(tagRepository).renameTag(TAG_ID, "JVM");
        verify(auditService).record(OPERATOR_ID, "TAG_RENAME", "tags", TAG_ID, "tagName=java->JVM, affectedPosts=3");
    }

    @Test
    @DisplayName("重命名：与其它标签重名 → 409 / 3008，不写库、不记审计")
    void renameCollisionIsRejected() {
        when(tagRepository.findTag(TAG_ID))
                .thenReturn(Optional.of(new TagRepository.TagSummary(TAG_ID, "java", 1, Instant.now())));
        when(tagRepository.findTagByName("JVM"))
                .thenReturn(Optional.of(new TagRepository.TagSummary(9L, "JVM", 1, Instant.now())));

        assertThatThrownBy(() -> service.rename(OPERATOR_ID, TAG_ID, "JVM"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TAG_NAME_TAKEN));

        verify(tagRepository, never()).renameTag(anyLong(), anyString());
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("重命名：放开空格 / 超 32 字 → 1001")
    void renameRejectsBlankAndTooLong() {
        assertThatThrownBy(() -> service.rename(OPERATOR_ID, TAG_ID, "   "))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> service.rename(OPERATOR_ID, TAG_ID, "x".repeat(33)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        verifyNoInteractions(tagRepository, auditService);
    }

    @Test
    @DisplayName("重命名：标签不存在 → 3001；新旧同名 → 幂等成功且不记审计")
    void renameMissingOrUnchanged() {
        when(tagRepository.findTag(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename(OPERATOR_ID, 9L, "JVM"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        when(tagRepository.findTag(TAG_ID))
                .thenReturn(Optional.of(new TagRepository.TagSummary(TAG_ID, "JVM", 1, Instant.now())));
        service.rename(OPERATOR_ID, TAG_ID, "JVM");

        verify(tagRepository, never()).renameTag(anyLong(), anyString());
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("重命名：并发撞唯一键 → 兜底为 3008")
    void renameDuplicateKeyFallsBackToTaken() {
        when(tagRepository.findTag(TAG_ID))
                .thenReturn(Optional.of(new TagRepository.TagSummary(TAG_ID, "java", 1, Instant.now())));
        when(tagRepository.findTagByName("JVM")).thenReturn(Optional.empty());
        when(tagRepository.renameTag(TAG_ID, "JVM"))
                .thenThrow(new DuplicateKeyException("uk_tags_name"));

        assertThatThrownBy(() -> service.rename(OPERATOR_ID, TAG_ID, "JVM"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.TAG_NAME_TAKEN));
    }

    @Test
    @DisplayName("合并：记 TAG_MERGE 审计（detail 含源 / 目标名与受影响帖子数）")
    void mergeRecordsAudit() {
        when(tagRepository.findTag(5L))
                .thenReturn(Optional.of(new TagRepository.TagSummary(5L, "Java", 3, Instant.now())));
        when(tagRepository.findTag(9L))
                .thenReturn(Optional.of(new TagRepository.TagSummary(9L, "后端", 2, Instant.now())));
        when(tagRepository.mergeTag(5L, 9L)).thenReturn(4);

        service.merge(OPERATOR_ID, 5L, 9L);

        verify(auditService).record(OPERATOR_ID, "TAG_MERGE", "tags", 5L,
                "source=Java, target=后端, affectedPosts=4");
    }

    @Test
    @DisplayName("合并：源与目标相同 → 1001；任一不存在 → 3001；均不写库、不记审计")
    void mergeRejectsSelfAndMissing() {
        assertThatThrownBy(() -> service.merge(OPERATOR_ID, 5L, 5L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));

        when(tagRepository.findTag(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.merge(OPERATOR_ID, 5L, 9L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        when(tagRepository.findTag(5L))
                .thenReturn(Optional.of(new TagRepository.TagSummary(5L, "Java", 3, Instant.now())));
        when(tagRepository.findTag(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.merge(OPERATOR_ID, 5L, 9L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND));

        verify(tagRepository, never()).mergeTag(anyLong(), anyLong());
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyLong(), anyString());
    }
}
