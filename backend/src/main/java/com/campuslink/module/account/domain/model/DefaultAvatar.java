package com.campuslink.module.account.domain.model;

import java.util.List;

/**
 * 默认头像目录（CR-162；CR-187 起为 8 款「连接结构」矢量徽章）：注册时按 userId 取模分配。
 *
 * <p>刻意做成**固定数组**而非运行时扫描 {@code public/avatars}：分配结果必须与部署目录无关，
 * 且同一 userId 在任何实例、任何时间都映射到同一张图。新增 / 删除素材要同时改这里与前端目录。
 *
 * <p>取模用 {@link Math#floorMod(long, int)}（负数也能落到合法下标），id 为 8 的倍数时映射到第 1 张。
 */
public final class DefaultAvatar {

    private static final List<String> PATHS = List.of(
            "/avatars/avatar-01-link.svg",
            "/avatars/avatar-02-network.svg",
            "/avatars/avatar-03-diamond.svg",
            "/avatars/avatar-04-brackets.svg",
            "/avatars/avatar-05-orbit.svg",
            "/avatars/avatar-06-core.svg",
            "/avatars/avatar-07-wave.svg",
            "/avatars/avatar-08-grid.svg");

    private DefaultAvatar() {
    }

    /** 该用户的默认头像路径；只依赖 id，纯函数、无随机、无 I/O。 */
    public static String forUser(long userId) {
        return PATHS.get(Math.floorMod(userId, PATHS.size()));
    }

    /** 目录全量（供回填脚本 / 测试核对素材一致性）。 */
    public static List<String> paths() {
        return PATHS;
    }
}
