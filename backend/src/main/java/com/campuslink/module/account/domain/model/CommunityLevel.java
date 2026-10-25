package com.campuslink.module.account.domain.model;

/**
 * 社区等级（F-GAME-001 / CR-138 / D2）：6 档，按声望阈值映射。
 *
 * <p>阈值是**下限闭区间**：0~49 初入、50~199 学徒、200~499 常客、500~999 贡献者、
 * 1000~1999 资深成员、2000 起社区之星。改阈值属产品口径变更，须另开 CR（D7），不做运行期配置。
 *
 * <p>档位只是展示口径：不做升级通知、不落库、不记录历史最高（D6）——声望是"当前可见贡献"的读数。
 */
public enum CommunityLevel {

    LV1(1, "初入", 0),
    LV2(2, "学徒", 50),
    LV3(3, "常客", 200),
    LV4(4, "贡献者", 500),
    LV5(5, "资深成员", 1000),
    LV6(6, "社区之星", 2000);

    private final int level;
    private final String displayName;
    private final long minReputation;

    CommunityLevel(int level, String displayName, long minReputation) {
        this.level = level;
        this.displayName = displayName;
        this.minReputation = minReputation;
    }

    /** 对外档位数字（1~6），徽章文案用 */
    public int level() {
        return level;
    }

    /** 对外档位名称（初入 / 学徒 / …），徽章文案用 */
    public String displayName() {
        return displayName;
    }

    /** 本档位的声望下限（含） */
    public long minReputation() {
        return minReputation;
    }

    /**
     * 声望落在哪一档：从高到低取第一个满足 {@code reputation >= minReputation} 的档位。
     *
     * <p>入参为负时按最低档处理——声望由四个非负计数加权而来，负数不可达，
     * 但"展示层不因一个不可达的输入抛异常"是本仓资料卡的既有纪律。
     */
    public static CommunityLevel of(long reputation) {
        CommunityLevel[] levels = values();
        for (int i = levels.length - 1; i >= 0; i--) {
            if (reputation >= levels[i].minReputation) {
                return levels[i];
            }
        }
        return LV1;
    }

    /** 下一档的声望门槛；满级（社区之星）无下一档，回 {@code null}（出参 {@code nextLevelAt} 随之不出现） */
    public Long nextThreshold() {
        CommunityLevel[] levels = values();
        int next = ordinal() + 1;
        return next < levels.length ? levels[next].minReputation : null;
    }

    /**
     * 当前档位内的进度百分比（0~100，向下取整）。
     *
     * <p>定义：{@code (声望 - 本档下限) / (下一档下限 - 本档下限) × 100}；满级恒 100。
     * 落在档位边界上时 {@link #of(long)} 已升档，故本方法在非满级下恒小于 100——
     * 恰好差 1 点升级时显示 98 之类的真实进度，而不是提前显示 100。
     */
    public int progressPercent(long reputation) {
        Long next = nextThreshold();
        if (next == null) {
            return 100;
        }
        long span = next - minReputation;
        long gained = Math.max(reputation - minReputation, 0);
        return (int) Math.min(gained * 100 / span, 100);
    }
}
