package com.campuslink.module.account.domain;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.module.account.domain.model.Account;
import com.campuslink.module.account.domain.model.AccountRole;
import com.campuslink.module.account.domain.model.AccountStatus;
import com.campuslink.module.account.domain.model.EmailAddress;
import com.campuslink.module.account.domain.model.ProfileField;
import com.campuslink.module.account.domain.model.StudentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 资料编辑的聚合规则（F-ACC-007a）：PUT 全量语义、归一化与差异列。
 * 差异集合是"定向 UPDATE 该 SET 哪几列"的唯一依据，所以它既不能多数（写了没改的列）
 * 也不能少数（改了却没落库）——两侧各有一条用例守着。
 */
class AccountProfileUpdateTest {

    /** U+200B 零宽空格：仿冒昵称的实测缺口（排序规则不折叠它），故服务端必须显式拒 */
    private static final char ZWSP = (char) 0x200B;

    @Test
    @DisplayName("只改给了值的字段：null 保持原值，差异集合恰为真变化的那一列")
    void nullMeansKeep() {
        Account account = account("张三同学", "软件工程", "想走 Java");

        Set<ProfileField> changed = account.updateProfile(null, "计算机科学与技术", null);

        assertThat(changed).containsExactly(ProfileField.MAJOR);
        assertThat(account.getMajor()).isEqualTo("计算机科学与技术");
        assertThat(account.getNickname()).isEqualTo("张三同学");
        assertThat(account.getBio()).isEqualTo("想走 Java");
    }

    @Test
    @DisplayName("trim 后与库中值相等 ⇒ 不算变化（幂等提交的判据）；传空串则清空为 NULL")
    void trimsAndClears() {
        Account account = account("张三同学", "软件工程", "想走 Java");

        Set<ProfileField> changed = account.updateProfile("  张三同学  ", "", "");

        // 昵称 trim 后全等 → 不在差异集合里；major/bio 清空为 NULL（不是空串，重复提交才幂等）
        assertThat(changed).containsExactlyInAnyOrder(ProfileField.MAJOR, ProfileField.BIO);
        assertThat(account.getNickname()).isEqualTo("张三同学");
        assertThat(account.getMajor()).isNull();
        assertThat(account.getBio()).isNull();
    }

    @Test
    @DisplayName("三值全等 → 差异集合为空：应用层据此不发 UPDATE、不推进 updated_at、不记审计")
    void identicalSubmissionYieldsNoChange() {
        Account account = account("张三同学", "软件工程", "想走 Java");

        assertThat(account.updateProfile("张三同学", "软件工程", "想走 Java")).isEmpty();
    }

    @Test
    @DisplayName("昵称不允许清空（空串与全空白都拒），过短 / 超长一律 1001")
    void nicknameMustStayWithinBounds() {
        assertRejectedWithBlankNickname("");
        assertRejectedWithBlankNickname("   ");
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile("一", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile("a".repeat(33), null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    private static void assertRejectedWithBlankNickname(String nickname) {
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile(nickname, null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("尖括号与控制字符拒绝；零宽字符**必须显式拒**（实测排序规则不折叠它，仿冒不会被白拿掉）")
    void rejectsMarkupAndZeroWidth() {
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile(null, null, "<script>alert(1)</script>"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile(null, null, "想\n走 Java"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
        // 零宽空格用 char 常量拼：源码里看得见、也不把不可见字符带进文件
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile("张三" + ZWSP + "同学", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("签名 200 字是产品上限（列宽 512 只是容量），多一字即 1001")
    void bioLengthCap() {
        assertThat(account("张三同学", null, null).updateProfile(null, null, "想".repeat(200)))
                .containsExactly(ProfileField.BIO);
        assertThatThrownBy(() -> account("张三同学", null, null).updateProfile(null, null, "想".repeat(201)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ResultCode.INVALID_PARAM));
    }

    @Test
    @DisplayName("非法值不该让聚合停在「改了一半」：合法字段也必须保持原值")
    void allOrNothing() {
        Account account = account("张三同学", "软件工程", "想走 Java");

        assertThatThrownBy(() -> account.updateProfile("新昵称", null, "<b>坏值</b>"))
                .isInstanceOf(ApiException.class);

        assertThat(account.getNickname()).isEqualTo("张三同学");
        assertThat(account.getBio()).isEqualTo("想走 Java");
    }

    @Test
    @DisplayName("资料编辑不碰账号生命周期字段：改完仍是原 role / status")
    void leavesRoleAndStatusAlone() {
        Account account = account("张三同学", null, null);

        account.updateProfile("新昵称", null, null);

        assertThat(account.getRole()).isEqualTo(AccountRole.USER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
    }

    private static Account account(String nickname, String major, String bio) {
        return Account.rehydrate(42L, EmailAddress.of("dev-stu-01@dev.campuslink.local"), null, StudentId.of("888800001"), nickname, null, null, major, null, bio, AccountRole.USER, AccountStatus.ACTIVE, true, false, null, null, null);
    }
}
