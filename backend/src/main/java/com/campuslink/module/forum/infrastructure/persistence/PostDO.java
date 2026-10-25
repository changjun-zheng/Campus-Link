package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** posts 表数据对象（MyBatis-Plus）。本类属基础设施，不进入领域层——互转见 {@link PostConverter}。 */
@Data
@TableName("posts")
public class PostDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long boardId;
    private Long authorId;
    private Boolean isAnonymous;
    private String type;
    private String title;
    private String contentMd;
    private String contentHtml;
    private String tags;
    private String status;
    private Boolean isDeleted;
    private Integer replyCount;
    private Integer likeCount;
    private Integer viewCount;
    private String coverUrl;
    private Integer favoriteCount;
    private Boolean isAccepted;
    private Long acceptedReplyId;
    private Double hotScore;
    private Instant createdAt;
    private Instant updatedAt;
}
