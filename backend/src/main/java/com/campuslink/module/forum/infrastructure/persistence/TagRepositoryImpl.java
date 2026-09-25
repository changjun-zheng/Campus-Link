package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostTagMapper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 适配器：TagRepository 端口的 MyBatis-Plus 实现（find-or-create + 关联 + 冗余列回写） */
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final com.campuslink.module.forum.infrastructure.persistence.mapper.PostMapper postMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void attachToPost(long postId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            TagDO tag = tagMapper.selectOne(new LambdaQueryWrapper<TagDO>().eq(TagDO::getName, name));
            if (tag == null) {
                tag = new TagDO();
                tag.setName(name);
                tag.setUseCount(0);
                try {
                    tagMapper.insert(tag);
                } catch (DuplicateKeyException ignored) {
                    // 并发建同名标签：唯一键兜底，回读既有行
                    tag = tagMapper.selectOne(new LambdaQueryWrapper<TagDO>().eq(TagDO::getName, name));
                }
            }
            PostTagDO link = new PostTagDO();
            link.setPostId(postId);
            link.setTagId(tag.getId());
            try {
                postTagMapper.insert(link);
            } catch (DuplicateKeyException ignored) {
                // 幂等：同一帖同一标签只挂一次
            }
            tagMapper.update(null, new LambdaUpdateWrapper<TagDO>()
                    .eq(TagDO::getId, tag.getId())
                    .setSql("use_count = use_count + 1"));
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(name);
        }
        // posts.tags 冗余列：列表展示与搜索 LIKE 兜底的数据源（V1 设计）
        postMapper.update(null, new LambdaUpdateWrapper<com.campuslink.module.forum.infrastructure.persistence.PostDO>()
                .eq(PostDO::getId, postId)
                .set(PostDO::getTags, joined.toString()));
    }
}
