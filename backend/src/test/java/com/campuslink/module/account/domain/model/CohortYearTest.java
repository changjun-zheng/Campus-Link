package com.campuslink.module.account.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入学年级归一化（CR-145 届徽章）的口径用例：开发库名册 grade 实测三种形态
 * （「模拟2024级」/「2023」/ NULL），归一只认首个 4 位年份数字。
 */
class CohortYearTest {

    @Test
    @DisplayName("「模拟2024级」→ 2024（D001 合成名册的实测形态）")
    void normalizesPrefixedGrade() {
        assertThat(CohortYear.normalize("模拟2024级")).isEqualTo("2024");
    }

    @Test
    @DisplayName("「2023」→ 2023（裸年份数字）")
    void normalizesBareYear() {
        assertThat(CohortYear.normalize("2023")).isEqualTo("2023");
    }

    @Test
    @DisplayName("null / 空白 / 无 4 位数字 → null（不快照，前端不渲染徽章）")
    void returnsNullWhenNoFourDigitYear() {
        assertThat(CohortYear.normalize(null)).isNull();
        assertThat(CohortYear.normalize("  ")).isNull();
        assertThat(CohortYear.normalize("研究生")).isNull();
        assertThat(CohortYear.normalize("24级")).isNull();
    }
}
