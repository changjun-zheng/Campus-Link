package com.campuslink.module.account.web;

import com.campuslink.common.result.ApiResponse;
import com.campuslink.common.result.ErrorCodes;
import com.campuslink.common.result.ResultCode;
import com.campuslink.common.web.IpUtil;
import com.campuslink.common.web.PublicEndpoint;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.account.application.CaptchaService;
import com.campuslink.module.account.application.cmd.AccountCommands.CaptchaCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.LoginResult;
import com.campuslink.module.account.application.cmd.AccountCommands.RegisterCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentCommand;
import com.campuslink.module.account.application.cmd.AccountCommands.VerifyStudentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号接口（web 层）：只做协议转换，用例逻辑在 application 层。
 * 路径与响应结构重构前后完全一致，前端零改动。
 */
@Tag(name = "auth", description = "学籍核验、注册与登录")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountApplicationService accountApplicationService;
    private final CaptchaService captchaService;

    @Operation(summary = "学籍核验（F-ACC-004）：学号 + 姓名，通过返回一次性核验票据")
    @ErrorCodes({ResultCode.STUDENT_VERIFY_FAILED, ResultCode.VERIFY_RATE_LIMITED})
    @PublicEndpoint
    @PostMapping("/verify-student")
    public ApiResponse<VerifyStudentResult> verifyStudent(@Valid @RequestBody VerifyStudentCommand command,
                                                          HttpServletRequest http) {
        return ApiResponse.ok(accountApplicationService.verifyStudent(command, IpUtil.clientIp(http)));
    }

    @Operation(summary = "发送验证码（F-011 / CR-093：target 为邮箱或手机号；60s 重发间隔，单账号日上限 10 条）")
    @ErrorCodes({ResultCode.CAPTCHA_TOO_FREQUENT, ResultCode.CAPTCHA_EXCEEDED})
    @PublicEndpoint
    @PostMapping("/captcha")
    public ApiResponse<Void> captcha(@Valid @RequestBody CaptchaCommand command) {
        captchaService.send(command.target());
        return ApiResponse.ok();
    }

    @Operation(summary = "注册：需先通过学籍核验并携带票据；contact 为邮箱或手机号（F-011 / CR-093）")
    @ErrorCodes({ResultCode.CAPTCHA_INVALID, ResultCode.VERIFY_TICKET_INVALID,
            ResultCode.STUDENT_VERIFY_FAILED, ResultCode.EMAIL_EXISTS, ResultCode.PHONE_EXISTS})
    @PublicEndpoint
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterCommand command) {
        return ApiResponse.ok(AuthResponse.from(accountApplicationService.register(command)));
    }

    @Operation(summary = "登录：邮箱 + 验证码")
    @ErrorCodes({ResultCode.LOGIN_FAILED, ResultCode.USER_BANNED, ResultCode.ACCOUNT_EXPIRED})
    @PublicEndpoint
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginCommand command) {
        return ApiResponse.ok(AuthResponse.from(accountApplicationService.login(command)));
    }

    /** web 响应模型：对前端契约与重构前一致 */
    public record AuthResponse(String token, long expiresAtEpochSecond, UserVo user) {

        public static AuthResponse from(LoginResult result) {
            return new AuthResponse(result.token(), result.expiresAtEpochSecond(), UserVo.from(result.account()));
        }
    }
}
