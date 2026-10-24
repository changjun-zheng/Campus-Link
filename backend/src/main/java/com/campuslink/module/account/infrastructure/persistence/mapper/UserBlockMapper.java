package com.campuslink.module.account.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.account.infrastructure.persistence.UserBlockDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** user_blocks 的 MyBatis-Plus Mapper（CR-129） */
@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlockDO> {

    /** 黑名单分页只取 blocked_id；参数化 LIMIT / OFFSET，不拼字符串 */
    @Select("SELECT blocked_id FROM user_blocks WHERE blocker_id = #{blockerId} "
            + "ORDER BY created_at DESC, blocked_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Long> pageBlockedIds(@Param("blockerId") long blockerId,
                              @Param("offset") int offset,
                              @Param("limit") int limit);
}
