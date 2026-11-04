package com.campuslink.module.account.domain.gateway;

/**
 * 头像图片存储端口（CR-191）：由 infrastructure 的本地磁盘适配器实现。
 *
 * <p>端口只表达"存一张图、得到一个可公开访问的路径"，不暴露文件系统细节——
 * 换对象存储（OSS）时新增适配器即可，应用层不改。
 */
public interface AvatarImageGateway {

    /**
     * 校验并保存一张头像，返回可公开访问的 URL（形如 {@code /media/avatars/{userId}/{uuid}.jpg}）。
     *
     * @param userId 目标账号 id（用于分目录，避免单目录文件过多）
     * @param raw    原始上传字节
     * @throws com.campuslink.common.exception.ApiException 格式 / 尺寸不合法（2016 / 400）
     */
    String store(long userId, byte[] raw);

    /**
     * 删除本存储写入的旧头像。实现必须只接受本适配器产出的路径前缀：
     * 预置头像（{@code /avatars/...}）与外部 URL 一律不删。删除失败只记日志，不抛异常。
     */
    void delete(String avatarUrl);
}
