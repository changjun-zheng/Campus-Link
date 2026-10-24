package com.campuslink.module.account.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuslink.module.account.infrastructure.persistence.StatusCountRow;
import com.campuslink.module.account.infrastructure.persistence.UserDO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper extends BaseMapper<UserDO> {

    /** 运营看板（F-ADMIN-005 / CR-131）：按 status 分组的只读计数，不做任何逐行扫描 */
    @Select("SELECT status, COUNT(*) AS cnt FROM users GROUP BY status")
    List<StatusCountRow> countByStatus();
}
