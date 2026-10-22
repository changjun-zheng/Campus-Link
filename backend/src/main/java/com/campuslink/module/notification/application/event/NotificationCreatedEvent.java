package com.campuslink.module.notification.application.event;

/**
 * 通知已落库（F-010）：只携带接收人，推送侧在事务提交后自行读未读数。
 *
 * <p>为什么不在事件里带未读计数：计数在同一事务内读到的是"含未提交行"的值，
 * 事务回滚就会推出一个不存在的数字；提交后再查一次是唯一正确的时点。
 */
public record NotificationCreatedEvent(long userId) {
}
