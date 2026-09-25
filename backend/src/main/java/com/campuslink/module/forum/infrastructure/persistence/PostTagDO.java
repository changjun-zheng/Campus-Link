package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** post_tags 表数据对象（V1 已建；联合主键 post_id + tag_id） */
@Data
@TableName("post_tags")
public class PostTagDO {

    private Long postId;
    private Long tagId;
}
