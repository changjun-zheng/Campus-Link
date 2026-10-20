package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.module.forum.domain.model.LikeTargetType;
import com.campuslink.module.forum.infrastructure.persistence.mapper.LikeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code LikeRepositoryImpl} 的点赞 toggle 与批量回读（F-FORUM-005，CR-111 覆盖率批）。
 *
 * <p>守 toggle 的**并发语义**：插入成功 = 本次点赞；撞主键 = 说明并发下别人刚赞过（或本次是取消），
 * 于是走 DELETE 并返回 false——两次并发 toggle 中恰有一次生效，靠的是数据库唯一键而不是先查后写。
 * 批量回读在空集合时短路（不产出 {@code IN ()}），非空时把 target_id 收成不可变 Set。
 *
 * <p>本类不证明数据库执行结果：断言落在 wrapper 条件与 mock 收到的参数上。
 */
@ExtendWith(MockitoExtension.class)
class LikeRepositoryImplTest {

    private static final long USER_ID = 42L;
    private static final long POST_ID = 7L;
    private static final long REPLY_ID = 99L;

    @Mock
    private LikeMapper likeMapper;

    private LikeRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(LikeMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, LikeDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new LikeRepositoryImpl(likeMapper);
    }

    @Test
    @DisplayName("首次点赞：insert 一行 (user, type, target)，返回 true（目标类型写枚举名）")
    void firstToggleInserts() {
        assertThat(repository.toggle(USER_ID, LikeTargetType.POST, POST_ID)).isTrue();

        ArgumentCaptor<LikeDO> captor = ArgumentCaptor.forClass(LikeDO.class);
        verify(likeMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTargetType()).isEqualTo(LikeTargetType.POST.name());
        assertThat(captor.getValue().getTargetId()).isEqualTo(POST_ID);
    }

    @Test
    @DisplayName("重复点赞（唯一键冲突）：转成取消，DELETE 三条件齐全并返回 false")
    void duplicateToggleDeletesAndReturnsFalse() {
        when(likeMapper.insert(any(LikeDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '42-POST-7' for key 'likes.PRIMARY'"));

        assertThat(repository.toggle(USER_ID, LikeTargetType.POST, POST_ID)).isFalse();

        LambdaQueryWrapper<LikeDO> query = capturedDelete();
        assertThat(query.getSqlSegment()).contains("user_id", "target_type", "target_id", "AND");
        assertThat(query.getParamNameValuePairs().values())
                .contains(USER_ID, LikeTargetType.POST.name(), POST_ID);
    }

    @Test
    @DisplayName("批量回读：空集合直接返回空 Set，不发 SQL（避免 IN () 非法语句）")
    void findLikedTargetIdsShortCircuitsOnEmpty() {
        assertThat(repository.findLikedTargetIds(USER_ID, LikeTargetType.POST, List.of())).isEmpty();

        verify(likeMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("批量回读：按用户 + 类型 + id IN 过滤，结果去重为不可变 Set")
    void findLikedTargetIdsMapsTargetIds() {
        List<Long> targetIds = List.of(POST_ID, REPLY_ID);
        when(likeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(like(POST_ID), like(REPLY_ID)));

        Set<Long> liked = repository.findLikedTargetIds(USER_ID, LikeTargetType.REPLY, targetIds);

        assertThat(liked).containsExactlyInAnyOrder(POST_ID, REPLY_ID);
        LambdaQueryWrapper<LikeDO> query = capturedSelectList();
        assertThat(query.getSqlSegment()).contains("user_id", "target_type", "target_id", "IN");
        assertThat(query.getParamNameValuePairs().values())
                .contains(USER_ID, LikeTargetType.REPLY.name(), POST_ID, REPLY_ID);
    }

    // ---------- helpers ----------

    private static LikeDO like(long targetId) {
        LikeDO d = new LikeDO();
        d.setUserId(USER_ID);
        d.setTargetType(LikeTargetType.REPLY.name());
        d.setTargetId(targetId);
        return d;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<LikeDO> capturedDelete() {
        ArgumentCaptor<Wrapper<LikeDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(likeMapper).delete(captor.capture());
        return (LambdaQueryWrapper<LikeDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<LikeDO> capturedSelectList() {
        ArgumentCaptor<Wrapper<LikeDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(likeMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        return (LambdaQueryWrapper<LikeDO>) captor.getValue();
    }
}
