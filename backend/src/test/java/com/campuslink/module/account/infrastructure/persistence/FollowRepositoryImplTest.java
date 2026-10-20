package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.module.account.infrastructure.persistence.mapper.FollowMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code FollowRepositoryImpl} 的关注关系读写（CR-074）。
 *
 * <p>守三条口径：① 重复关注**幂等**——联合主键冲突被吞掉，既不抛给调用方也不产生删行副作用；
 * ② 计数与存在性必须**区分方向**（{@code follower_id} 是「谁关注」，{@code followee_id} 是「被关注」），
 * 两列写串会让「关注数 / 粉丝数」互相冒充；③ 关注列表有数量上限，{@code limit<=0} 时兜底为 1，
 * 绝不让 {@code LIMIT 0} 或拼接空串进入 SQL。
 *
 * <p>本类不证明数据库执行结果：断言全部落在 wrapper 的
 * {@code getSqlSegment()} / {@code getParamNameValuePairs()} 与 mock 收到的参数上。
 */
@ExtendWith(MockitoExtension.class)
class FollowRepositoryImplTest {

    private static final long FOLLOWER_ID = 42L;
    private static final long FOLLOWEE_ID = 7L;

    @Mock
    private FollowMapper followMapper;

    private FollowRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(FollowMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, FollowDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FollowRepositoryImpl(followMapper);
    }

    @Test
    @DisplayName("关注：insert 一行 (follower_id, followee_id)，两个方向各写自己那一列")
    void followInsertsDirectionalRow() {
        repository.follow(FOLLOWER_ID, FOLLOWEE_ID);

        ArgumentCaptor<FollowDO> captor = ArgumentCaptor.forClass(FollowDO.class);
        verify(followMapper).insert(captor.capture());
        assertThat(captor.getValue().getFollowerId()).isEqualTo(FOLLOWER_ID);
        assertThat(captor.getValue().getFolloweeId()).isEqualTo(FOLLOWEE_ID);
    }

    @Test
    @DisplayName("重复关注：唯一键冲突即幂等，不抛异常也不删行（与取消关注严格区分）")
    void duplicateFollowIsIdempotent() {
        when(followMapper.insert(any(FollowDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '42-7' for key 'follows.PRIMARY'"));

        assertThatCode(() -> repository.follow(FOLLOWER_ID, FOLLOWEE_ID)).doesNotThrowAnyException();
        verify(followMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("取消关注：DELETE 同时带 follower_id 与 followee_id（单向，不误删反向关注）")
    void unfollowDeletesOnlyThatDirection() {
        repository.unfollow(FOLLOWER_ID, FOLLOWEE_ID);

        LambdaQueryWrapper<FollowDO> query = capturedDelete();
        assertThat(query.getSqlSegment()).contains("follower_id", "followee_id", "AND");
        assertThat(query.getParamNameValuePairs().values()).contains(FOLLOWER_ID, FOLLOWEE_ID);
    }

    @Test
    @DisplayName("存在性：计数 >0 为 true、0 为 false，条件仍是双向配对")
    void existsByFollowerAndFolloweeCounts() {
        when(followMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);

        assertThat(repository.existsByFollowerAndFollowee(FOLLOWER_ID, FOLLOWEE_ID)).isTrue();
        assertThat(repository.existsByFollowerAndFollowee(FOLLOWER_ID, FOLLOWEE_ID)).isFalse();

        String sql = capturedSelectCount().getSqlSegment();
        assertThat(sql).contains("follower_id", "followee_id");
    }

    @Test
    @DisplayName("被关注数（粉丝）：只按 followee_id 过滤")
    void countByFolloweeFiltersOnFolloweeId() {
        when(followMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        assertThat(repository.countByFollowee(FOLLOWEE_ID)).isEqualTo(3L);

        LambdaQueryWrapper<FollowDO> query = capturedSelectCount();
        assertThat(query.getSqlSegment()).contains("followee_id").doesNotContain("follower_id");
        assertThat(query.getParamNameValuePairs().values()).contains(FOLLOWEE_ID);
    }

    @Test
    @DisplayName("关注数：只按 follower_id 过滤（与被关注数方向相反，两列不得写串）")
    void countByFollowerFiltersOnFollowerId() {
        when(followMapper.selectCount(any(Wrapper.class))).thenReturn(5L);

        assertThat(repository.countByFollower(FOLLOWER_ID)).isEqualTo(5L);

        LambdaQueryWrapper<FollowDO> query = capturedSelectCount();
        assertThat(query.getSqlSegment()).contains("follower_id").doesNotContain("followee_id");
        assertThat(query.getParamNameValuePairs().values()).contains(FOLLOWER_ID);
    }

    @Test
    @DisplayName("关注列表：按 created_at 升序取 followee_id，limit 原样进 LIMIT")
    void findFolloweeIdsOrdersByCreatedAtAsc() {
        when(followMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(link(7L), link(8L)));

        assertThat(repository.findFolloweeIds(FOLLOWER_ID, 5)).containsExactly(7L, 8L);

        LambdaQueryWrapper<FollowDO> query = capturedSelectList();
        assertThat(query.getSqlSegment()).contains("follower_id", "created_at", "ORDER BY", "LIMIT 5");
    }

    @Test
    @DisplayName("关注列表：limit<=0 兜底为 LIMIT 1（绝不产出 LIMIT 0 的空页）")
    void findFolloweeIdsFloorsLimitToOne() {
        when(followMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        repository.findFolloweeIds(FOLLOWER_ID, 0);

        assertThat(capturedSelectList().getSqlSegment()).contains("LIMIT 1");
    }

    // ---------- helpers ----------

    private static FollowDO link(long followeeId) {
        FollowDO d = new FollowDO();
        d.setFollowerId(FOLLOWER_ID);
        d.setFolloweeId(followeeId);
        return d;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<FollowDO> capturedDelete() {
        ArgumentCaptor<Wrapper<FollowDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(followMapper).delete(captor.capture());
        return (LambdaQueryWrapper<FollowDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<FollowDO> capturedSelectCount() {
        ArgumentCaptor<Wrapper<FollowDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(followMapper, org.mockito.Mockito.atLeastOnce()).selectCount(captor.capture());
        return (LambdaQueryWrapper<FollowDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<FollowDO> capturedSelectList() {
        ArgumentCaptor<Wrapper<FollowDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(followMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        return (LambdaQueryWrapper<FollowDO>) captor.getValue();
    }
}
