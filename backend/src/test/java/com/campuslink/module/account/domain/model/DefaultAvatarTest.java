package com.campuslink.module.account.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 默认头像目录（CR-162；CR-187 起 8 款矢量徽章）：取模稳定、8 张全覆盖、路径与素材目录同源。 */
class DefaultAvatarTest {

    @Test
    @DisplayName("同一 userId 恒映射同一张；1~8 恰好覆盖全部 8 张")
    void mapsStablyAndCoversAll() {
        Set<String> seen = new HashSet<>();
        for (long id = 1; id <= 8; id++) {
            String path = DefaultAvatar.forUser(id);
            seen.add(path);
            assertThat(DefaultAvatar.forUser(id)).isEqualTo(path);
        }
        assertThat(seen).hasSize(8);
        assertThat(seen).isEqualTo(new HashSet<>(DefaultAvatar.paths()));
    }

    @Test
    @DisplayName("取模边界：8 的倍数回第 1 张，0 与负数也落在合法下标")
    void moduloEdges() {
        assertThat(DefaultAvatar.forUser(24)).isEqualTo("/avatars/avatar-01-link.svg");
        assertThat(DefaultAvatar.forUser(48)).isEqualTo("/avatars/avatar-01-link.svg");
        assertThat(DefaultAvatar.forUser(25)).isEqualTo("/avatars/avatar-02-network.svg");
        assertThat(DefaultAvatar.forUser(0)).isEqualTo("/avatars/avatar-01-link.svg");
        assertThat(DefaultAvatar.forUser(-1)).isEqualTo("/avatars/avatar-08-grid.svg");
    }

    @Test
    @DisplayName("目录项均为 public 下的 SVG 绝对路径（与 frontend/public/avatars 一致）")
    void pathsArePublicPngs() {
        assertThat(DefaultAvatar.paths()).isNotEmpty()
                .allSatisfy(path -> assertThat(path).startsWith("/avatars/").endsWith(".svg"));
    }
}
