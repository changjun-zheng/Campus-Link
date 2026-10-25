package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.domain.model.Reply;
import com.campuslink.module.forum.infrastructure.persistence.mapper.ReplyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配器可见性过滤测试（CR-060 / 问题清单 BUG-002）。
 *
 * <p>楼层写用例（点赞、采纳）的"目标是否可见"由 SQL 侧条件决定，应用层 Mockito 结构上看不见列语义
 * （同 {@code NotificationRepositoryImplFilterTest} 记过的教训），故把 wrapper 捕获出来直接断言渲染的
 * 条件与绑定值：{@code findVisibleById} 必须同时带 {@code status='PUBLISHED'} 与 {@code is_deleted=0}，
 * 且与列表方法 {@code findPageByPostId} 的过滤**同源**——否则就是"前台看不到、仍可操作"的读写口径分裂。
 */
@ExtendWith(MockitoExtension.class)
class ReplyRepositoryImplVisibleTest {

    private static final Pattern STATUS_PARAM =
            Pattern.compile("status\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");
    private static final Pattern IS_DELETED_PARAM =
            Pattern.compile("is_deleted\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    private static final long REPLY_ID = 11L;
    private static final long POST_ID = 9L;

    @Mock
    private ReplyMapper replyMapper;

    private ReplyRepositoryImpl repository;

    /** 纯单测没有 SqlSession，lambda 条件的列名解析依赖 MP 的 TableInfo 缓存，先手工预热 */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ReplyMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ReplyDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ReplyRepositoryImpl(replyMapper);
    }

    private LambdaQueryWrapper<ReplyDO> captured(Query kind) {
        if (kind == Query.SINGLE) {
            when(replyMapper.selectOne(any())).thenReturn(null);
            repository.findVisibleById(REPLY_ID);
            ArgumentCaptor<Wrapper<ReplyDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(replyMapper).selectOne(captor.capture());
            return (LambdaQueryWrapper<ReplyDO>) captor.getValue();
        }
        when(replyMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ReplyDO> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of());
            page.setTotal(0);
            return page;
        });
        repository.findPageByPostId(POST_ID, java.util.Set.of(), java.util.Set.of(), 1, 20);
        ArgumentCaptor<Wrapper<ReplyDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(replyMapper).selectPage(any(IPage.class), captor.capture());
        return (LambdaQueryWrapper<ReplyDO>) captor.getValue();
    }

    private enum Query {SINGLE, PAGE}

    private Object bound(LambdaQueryWrapper<ReplyDO> query, Pattern pattern) {
        Matcher matcher = pattern.matcher(query.getSqlSegment());
        assertThat(matcher.find()).as("条件里应出现该列，实际：%s", query.getSqlSegment());
        return query.getParamNameValuePairs().get(matcher.group(1));
    }

    @Test
    @DisplayName("findVisibleById：SQL 同时下发 status='PUBLISHED' 与 is_deleted=0")
    void visibleQueryCarriesBothConditions() {
        LambdaQueryWrapper<ReplyDO> query = captured(Query.SINGLE);

        assertThat(bound(query, STATUS_PARAM)).isEqualTo("PUBLISHED");
        assertThat(bound(query, IS_DELETED_PARAM)).isEqualTo(false);
        assertThat(query.getSqlSegment()).contains("id =");
    }

    @Test
    @DisplayName("与列表 findPageByPostId 的可见性条件同源（读写口径一致）")
    void writeSideFilterMatchesListFilter() {
        LambdaQueryWrapper<ReplyDO> single = captured(Query.SINGLE);
        LambdaQueryWrapper<ReplyDO> page = captured(Query.PAGE);

        // 逐列比对两侧绑定的值：任一侧丢失 status / is_deleted 条件，bound() 即因正则无匹配而失败
        assertThat(bound(single, STATUS_PARAM)).isEqualTo(bound(page, STATUS_PARAM));
        assertThat(bound(single, IS_DELETED_PARAM)).isEqualTo(bound(page, IS_DELETED_PARAM));
        // 同一条件只在读侧表达一次，计数更新与列表查询都不重复表达
        assertThat(count(single.getSqlSegment(), "status")).isEqualTo(1);
        assertThat(count(single.getSqlSegment(), "is_deleted")).isEqualTo(1);
        assertThat(count(page.getSqlSegment(), "status")).isEqualTo(1);
        assertThat(count(page.getSqlSegment(), "is_deleted")).isEqualTo(1);
    }

    @Test
    @DisplayName("下架楼层查不到时返回空 Optional（交由用例统一抛 3001，不在适配器拼错误体）")
    void removedReplyYieldsEmpty() {
        when(replyMapper.selectOne(any())).thenReturn(null);

        Optional<Reply> result = repository.findVisibleById(REPLY_ID);

        assertThat(result).isEmpty();
    }

    /**
     * CR-085 引用摘要的批量版：条件必须与单条版 {@code findVisibleById} 同源——
     * 若少了 status 或 is_deleted，被下架 / 已删除楼层的内容就会经引用块重新外泄给读者。
     */
    @Test
    @DisplayName("findVisibleByIds：IN + status='PUBLISHED' + is_deleted=0 三条件与单条版同源")
    void visibleIdsQueryCarriesSameConditionsAsSingle() {
        when(replyMapper.selectList(any())).thenReturn(java.util.List.of());
        repository.findVisibleByIds(java.util.List.of(11L, 12L));

        ArgumentCaptor<Wrapper<ReplyDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(replyMapper).selectList(captor.capture());
        LambdaQueryWrapper<ReplyDO> query = (LambdaQueryWrapper<ReplyDO>) captor.getValue();

        assertThat(bound(query, STATUS_PARAM)).isEqualTo("PUBLISHED");
        assertThat(bound(query, IS_DELETED_PARAM)).isEqualTo(false);
        assertThat(query.getSqlSegment()).contains("IN");
        assertThat(count(query.getSqlSegment(), "status")).isEqualTo(1);
        assertThat(count(query.getSqlSegment(), "is_deleted")).isEqualTo(1);
    }

    private int count(String segment, String column) {
        int n = 0;
        for (int i = segment.indexOf(column); i >= 0; i = segment.indexOf(column, i + column.length())) {
            n++;
        }
        return n;
    }
}
