package com.campuslink.module.forum.domain.gateway;

/**
 * 出站端口：内容机审（F-SAFE-001，CR-082）。发帖 / 回帖发布前对纯文本调用；
 * 实现位于 infrastructure（log 通道为本轮交付，真实服务商适配待凭据，见实施方案 §2）。
 *
 * <p>fail-closed 语义（技术方案 §7.3）：实现内部异常由应用层统一转 5002（拦发布、不放行）——
 * 宁可暂时关发布入口，不可跳过审核。
 */
public interface ModerationGateway {

    ModerationVerdict moderate(String content);

    /** 机审结论：pass = 放行；blocked 时 reason 为通用违规原因（不透出规则细节） */
    record ModerationVerdict(boolean pass, String reason) {

        public static ModerationVerdict ok() {
            return new ModerationVerdict(true, null);
        }

        public static ModerationVerdict blocked(String reason) {
            return new ModerationVerdict(false, reason);
        }
    }
}
