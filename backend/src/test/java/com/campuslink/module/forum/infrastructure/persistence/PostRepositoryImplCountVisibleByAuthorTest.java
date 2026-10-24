package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 他人主页「帖子 N」计数的 SQL 结构测试（F-ACC-002 本体最小版）。
 *
 * <p>与 {@code PostRepositoryImplFindPageByAuthorTest} 恰好是**镜像的一对**：那条读路径刻意不写
 * {@code status}（作者本人要看见被下架的帖），这条**必须写** {@code status} 与 {@code is_deleted}
 * （路人视角，已下架与已删除一律不计）。两边都靠"断言 SQL 片段"而不是断言返回值来守——
 * 条件写漏了，计数照样是个数字，只有对着 WHERE 才看得出来。
 *
 * <p>还有一格专守"同源"：计数与 {@code findPageByAuthors}（主页那份列表）必须给出同一组可见性条件，
 * 否则资料卡上的「帖子 12」与它下方只有 10 条的列表会对不上，而这种偏差在前端无从察觉。
 */
@ExtendWith(MockitoExtension.class)
class PostRepositoryImplCountVisibleByAuthorTest {

    private static final long AUTHOR_ID = 42L;

    @Mock
    private PostMapper postMapper;

    private PostRepositoryImpl repository;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(PostMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, PostDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new PostRepositoryImpl(postMapper);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PostDO> capturedCountQuery() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).selectCount(captor.capture());
        return (LambdaQueryWrapper<PostDO>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<PostDO> capturedPageQuery() {
        ArgumentCaptor<Wrapper<PostDO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).selectPage(any(IPage.class), captor.capture());
        return (LambdaQueryWrapper<PostDO>) captor.getValue();
    }

    @Test
    @DisplayName("谓词为 author_id + status + is_deleted 三条齐全，计数不带 ORDER BY（排序对 COUNT 无意义且会拖慢）")
    void countsOnlyPublishedAndUndeletedRowsOfThatAuthor() {
        when(postMapper.selectCount(any())).thenReturn(3L);

        assertThat(repository.countVisibleByAuthor(AUTHOR_ID)).isEqualTo(3L);

        LambdaQueryWrapper<PostDO> query = capturedCountQuery();
        assertThat(query.getSqlSegment()).contains("author_id", "status", "is_deleted");
        assertThat(query.getSqlSegment()).doesNotContain("ORDER BY");
        assertThat(query.getParamNameValuePairs()).containsValue(AUTHOR_ID);
        assertThat(query.getParamNameValuePairs().values()).contains("PUBLISHED", false);
    }

    @Test
    @DisplayName("同源：计数与 findPageByAuthors（主页列表）渲染出的可见性条件逐列一致")
    void sharesVisibilityConditionsWithTheListQuery() {
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(postMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20).setRecords(List.of()).setTotal(0L));

        repository.countVisibleByAuthor(AUTHOR_ID);
        repository.findPageByAuthors(List.of(AUTHOR_ID), java.util.Set.of(), 1, 20);

        assertThat(visibilitySignature(capturedCountQuery())).isEqualTo(visibilitySignature(capturedPageQuery()));
    }

    /**
     * 把 wrapper 归一成「可见性签名」：WHERE 里出现了哪几列 + 这些列绑定的是什么值。
     *
     * <p>刻意丢掉 {@code IN} 与 {@code =} 的写法差异和 ORDER BY：计数用等值、列表用 IN，
     * 但"筛的是同一批行"这件事只由列与值决定。任一侧漏写一列或改了一个值，签名立刻不等。
     */
    private static String visibilitySignature(LambdaQueryWrapper<PostDO> query) {
        String sql = query.getSqlSegment();
        Map<String, String> columns = new TreeMap<>();
        for (String column : List.of("author_id", "status", "is_deleted")) {
            columns.put(column, sql.contains(column) ? "present" : "MISSING");
        }
        Set<String> values = new TreeSet<>(query.getParamNameValuePairs().values().stream()
                .map(String::valueOf).toList());
        return columns + " values=" + values;
    }
}
