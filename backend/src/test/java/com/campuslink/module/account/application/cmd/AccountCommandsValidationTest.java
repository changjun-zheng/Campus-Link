package com.campuslink.module.account.application.cmd;

import com.campuslink.module.account.application.cmd.AccountCommands.UpdateProfileCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 命令层校验注解的接线测试（CR-123）：规则本身由 {@code FieldRulesContactPatternTest} 证明，
 * 这里只证明「专业」字段真的挂上了引流 / 联系方式禁则——纯代码侧规则不进契约，只能靠单测兜底。
 */
class AccountCommandsValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("专业含显式引流 → 校验失败（与昵称 / 签名同一禁则）")
    void majorWithContactInfoIsRejected() {
        var violations = VALIDATOR.validate(new UpdateProfileCommand(null, "加微信 abc123", null));

        assertThat(violations).anySatisfy(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("major");
            assertThat(violation.getMessage()).contains("站外联系方式");
        });
    }

    @Test
    @DisplayName("正常专业放行（禁则只挡显式引流，不误伤专业名）")
    void normalMajorPasses() {
        assertThat(VALIDATOR.validate(new UpdateProfileCommand(null, "计算机科学与技术", null))).isEmpty();
    }
}
