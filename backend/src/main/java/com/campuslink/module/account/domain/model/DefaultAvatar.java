package com.campuslink.module.account.domain.model;

import java.util.List;

/**
 * 默认头像目录（CR-162）：24 张随素材入库的固定图片，注册时按 userId 取模分配。
 *
 * <p>刻意做成**固定数组**而非运行时扫描 {@code public/avatars}：分配结果必须与部署目录无关，
 * 且同一 userId 在任何实例、任何时间都映射到同一张图。新增 / 删除素材要同时改这里与前端目录。
 *
 * <p>取模用 {@link Math#floorMod(long, int)}（负数也能落到合法下标），id 为 24 的倍数时映射到第 1 张。
 */
public final class DefaultAvatar {

    private static final List<String> PATHS = List.of(
            "/avatars/avatar-01-terminal.png",
            "/avatars/avatar-02-keyboard.png",
            "/avatars/avatar-03-gear.png",
            "/avatars/avatar-04-cloud.png",
            "/avatars/avatar-05-cube.png",
            "/avatars/avatar-06-lightbulb.png",
            "/avatars/avatar-07-headphones.png",
            "/avatars/avatar-08-robot.png",
            "/avatars/avatar-09-chip.png",
            "/avatars/avatar-10-database.png",
            "/avatars/avatar-11-network.png",
            "/avatars/avatar-12-mouse.png",
            "/avatars/avatar-13-monitor.png",
            "/avatars/avatar-14-rocket.png",
            "/avatars/avatar-15-coffee.png",
            "/avatars/avatar-16-bolt.png",
            "/avatars/avatar-17-cursor.png",
            "/avatars/avatar-18-wifi.png",
            "/avatars/avatar-19-wrench.png",
            "/avatars/avatar-20-gradcap.png",
            "/avatars/avatar-21-book.png",
            "/avatars/avatar-22-backpack.png",
            "/avatars/avatar-23-pencil.png",
            "/avatars/avatar-24-globe.png");

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
