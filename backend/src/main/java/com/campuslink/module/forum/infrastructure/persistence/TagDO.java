package com.campuslink.module.forum.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** tags 表数据对象（V1 已建；CR-083 标签闭环首次启用） */
@Data
@TableName("tags")
public class TagDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer useCount;
    private Instant createdAt;
    private Instant updatedAt;
}
