package com.campuslink.module.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campuslink.module.account.infrastructure.persistence.mapper.UserBlockMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockRepositoryImplTest {

    @Mock
    private UserBlockMapper userBlockMapper;
    @InjectMocks
    private UserBlockRepositoryImpl repository;

    @Test
    @DisplayName("重复拉黑撞联合主键 → 返回 false（幂等，不抛错）")
    void duplicateBlockIsIdempotent() {
        when(userBlockMapper.insert(any(UserBlockDO.class))).thenThrow(new DuplicateKeyException("uk"));

        assertThat(repository.block(1L, 8L)).isFalse();
    }

    @Test
    @DisplayName("新拉黑 → true；解除走定向删除")
    void blockAndUnblock() {
        when(userBlockMapper.insert(any(UserBlockDO.class))).thenReturn(1);
        assertThat(repository.block(1L, 8L)).isTrue();

        repository.unblock(1L, 8L);
        verify(userBlockMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("黑名单分页：参数化 offset / limit 透传给 Mapper")
    void pageBlockedIdsPassesPaging() {
        when(userBlockMapper.pageBlockedIds(1L, 20, 10)).thenReturn(List.of(8L, 9L));

        assertThat(repository.pageBlockedIds(1L, 20, 10)).containsExactly(8L, 9L);
    }
}
