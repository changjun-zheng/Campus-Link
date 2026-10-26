package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campuslink.module.forum.domain.gateway.TagRepository;
import com.campuslink.module.forum.domain.gateway.TagRepository.TagSummary;
import com.campuslink.module.forum.infrastructure.persistence.mapper.PostTagMapper;
import com.campuslink.module.forum.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void replaceForPost(long postId, List<String> names) {
        // 先解除旧关联并回退旧标签的使用数（与 attachToPost 的 +1 对称），避免编辑几次后计数只增不减
        List<PostTagDO> existing = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTagDO>().eq(PostTagDO::getPostId, postId));
        for (PostTagDO link : existing) {
            tagMapper.update(null, new LambdaUpdateWrapper<TagDO>()
                    .eq(TagDO::getId, link.getTagId())
                    .setSql("use_count = GREATEST(use_count - 1, 0)"));
        }
        postTagMapper.delete(new LambdaQueryWrapper<PostTagDO>().eq(PostTagDO::getPostId, postId));
        // 空标签时 attachToPost 直接返回，冗余列必须在这里显式清空（否则旧标签仍能被搜索 LIKE 命中）
        postMapper.update(null, new LambdaUpdateWrapper<com.campuslink.module.forum.infrastructure.persistence.PostDO>()
                .eq(PostDO::getId, postId)
                .set(PostDO::getTags, null));
        attachToPost(postId, names);
    }

    @Override
    public List<TagSummary> pageTags(String keyword, String sort, int offset, int limit) {
        return tagMapper.pageTags(normalizeKeyword(keyword), normalizeSort(sort),
                        Math.max(offset, 0), Math.max(limit, 1)).stream()
                .map(TagRepositoryImpl::toSummary)
                .toList();
    }

    @Override
    public long countTags(String keyword) {
        return tagMapper.countTags(normalizeKeyword(keyword));
    }

    @Override
    public Optional<TagSummary> findTag(long tagId) {
        return Optional.ofNullable(tagMapper.findTag(tagId)).map(TagRepositoryImpl::toSummary);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int deleteTag(long tagId) {
        TagDO tag = tagMapper.selectById(tagId);
        if (tag == null) {
            return 0;
        }
        List<Long> postIds = postTagMapper.findPostIdsByTagId(tagId);
        // 顺序不可换：fk_pt_tag 外键要求先删关联；冗余列要在删 tags 行前拿到标签名
        postTagMapper.delete(new LambdaQueryWrapper<PostTagDO>().eq(PostTagDO::getTagId, tagId));
        for (Long postId : postIds) {
            removeTagFromRedundancy(postId, tag.getName());
        }
        tagMapper.deleteById(tagId);
        return postIds.size();
    }

    /** 从单帖的 posts.tags 冗余列里移除指定标签名（保留其余标签；清空则置 NULL） */
    private void removeTagFromRedundancy(Long postId, String tagName) {
        com.campuslink.module.forum.infrastructure.persistence.PostDO post = postMapper.selectById(postId);
        if (post == null || post.getTags() == null || post.getTags().isBlank()) {
            return;
        }
        String remaining = java.util.Arrays.stream(post.getTags().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals(tagName))
                .collect(java.util.stream.Collectors.joining(","));
        postMapper.update(null, new LambdaUpdateWrapper<com.campuslink.module.forum.infrastructure.persistence.PostDO>()
                .eq(com.campuslink.module.forum.infrastructure.persistence.PostDO::getId, postId)
                .set(com.campuslink.module.forum.infrastructure.persistence.PostDO::getTags,
                        remaining.isEmpty() ? null : remaining));
    }

    private static TagSummary toSummary(AdminTagRow row) {
        return new TagSummary(row.getId(), row.getName(),
                row.getUseCount() == null ? 0 : row.getUseCount(), row.getCreatedAt());
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /** 排序键归一：只认 name，其余（含 null / 任意串）一律 use_count 降序 */
    private static String normalizeSort(String sort) {
        return "name".equals(sort) ? "name" : "use_count";
    }
}
