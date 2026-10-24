package com.campuslink.module.forum.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.forum.infrastructure.persistence.PostTagDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PostTagMapper extends BaseMapper<PostTagDO> {

    /** 删除标签前取受影响帖子（F-ADMIN-004 标签侧 / CR-132）：用于清理 posts.tags 冗余列并记审计 */
    @Select("SELECT post_id FROM post_tags WHERE tag_id = #{tagId}")
    List<Long> findPostIdsByTagId(@Param("tagId") long tagId);
}
