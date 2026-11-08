package com.campuslink.module.notification.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campuslink.module.notification.domain.gateway.NotificationRepository;
import com.campuslink.module.notification.domain.gateway.PageResult;
import com.campuslink.module.notification.domain.model.Notification;
import com.campuslink.module.notification.domain.model.NotificationTargetType;
import com.campuslink.module.notification.domain.model.NotificationType;
import com.campuslink.module.notification.infrastructure.persistence.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 适配器：NotificationRepository 端口的 MyBatis-Plus 实现 */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMapper notificationMapper;

    @Override
    public void save(Notification notification) {
        notificationMapper.insert(toDo(notification));
    }

    @Override
    public Optional<Notification> findById(long notificationId) {
        return Optional.ofNullable(notificationMapper.selectById(notificationId))
                .map(NotificationRepositoryImpl::toDomain);
    }

    @Override
    public PageResult<Notification> findPage(long userId, Boolean unread, int page, int size) {
        LambdaQueryWrapper<NotificationDO> query = new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getUserId, userId);
        if (unread != null) {
            // 入参是「只看未读」，列是 isRead，语义相反：unread=true 要筛 is_read=0
            query.eq(NotificationDO::getIsRead, !unread);
        }
        query.orderByDesc(NotificationDO::getCreatedAt).orderByDesc(NotificationDO::getId);
        IPage<NotificationDO> result = notificationMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(NotificationRepositoryImpl::toDomain).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public long countUnread(long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getIsRead, false));
    }

    @Override
    public int markAllRead(long userId) {
        // is_read=0 前置条件让 UPDATE 只命中未读行：affected rows 即"本次新标记的条数"，重复调用返回 0
        return notificationMapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                .set(NotificationDO::getIsRead, true)
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getIsRead, false));
    }

    @Override
    public boolean markRead(long notificationId, long userId) {
        // 与 markAllRead 同一口径：is_read=0 前置条件 + 接收人条件，只可能命中本人的那一行未读记录
        return notificationMapper.update(null, new LambdaUpdateWrapper<NotificationDO>()
                .set(NotificationDO::getIsRead, true)
                .eq(NotificationDO::getId, notificationId)
                .eq(NotificationDO::getUserId, userId)
                .eq(NotificationDO::getIsRead, false)) > 0;
    }

    private static NotificationDO toDo(Notification notification) {
        NotificationDO d = new NotificationDO();
        d.setUserId(notification.getUserId());
        d.setType(notification.getType().code());
        d.setActorId(notification.getActorId());
        d.setTargetType(notification.getTargetType().name());
        d.setTargetId(notification.getTargetId());
        d.setMessage(notification.getMessage());
        d.setIsRead(false);
        d.setCreatedAt(notification.getCreatedAt());
        return d;
    }

    private static Notification toDomain(NotificationDO d) {
        return Notification.rehydrate(d.getId(), d.getUserId(), NotificationType.fromCode(d.getType()),
                d.getActorId(), NotificationTargetType.fromName(d.getTargetType()), d.getTargetId(), d.getMessage(),
                Boolean.TRUE.equals(d.getIsRead()), d.getCreatedAt());
    }
}
