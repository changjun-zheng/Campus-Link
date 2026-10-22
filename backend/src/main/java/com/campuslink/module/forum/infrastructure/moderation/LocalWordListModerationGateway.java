package com.campuslink.module.forum.infrastructure.moderation;

import com.campuslink.module.forum.domain.gateway.ModerationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 机审本地词表通道（N4 / BUG-006，CR-118）：`campuslink.moderation.mode=local` 时装配。
 *
 * <p><b>定位</b>：在真实服务商（A3-8）落地前，给单校封闭社区一个**可执行的**内容闸门——
 * 命中词表即 `5001` 拦发布，未命中放行；命中只回通用原因、不吐规则细节。
 *
 * <p><b>fail-closed 的两处体现</b>：① 词表为空时**构造即失败**（启动中止），不允许"配了 local
 * 但一个词都没有"变成静默放行；② 调用方（Post / ReplyApplicationService）对任何异常一律 `5002`
 * 拦发布，本类不吞异常。
 *
 * <p><b>匹配口径</b>：大小写不敏感的子串匹配（`Locale.ROOT`），不做分词 / 正则——词表由部署方维护，
 * 宁可漏判也不误伤；误伤由人工复核与举报兜底。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "campuslink.moderation.mode", havingValue = "local")
public class LocalWordListModerationGateway implements ModerationGateway {

    private final List<String> words;

    public LocalWordListModerationGateway(@Value("${campuslink.moderation.words:}") String rawWords) {
        this.words = Arrays.stream(rawWords.split(","))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        if (words.isEmpty()) {
            throw new IllegalStateException(
                    "campuslink.moderation.mode=local 时必须配置 campuslink.moderation.words（逗号分隔）——"
                            + "空词表等于静默放行，与 fail-closed 红线冲突");
        }
        log.info("[MODERATION:local] 本地词表已装载：{} 个词", words.size());
    }

    @Override
    public ModerationVerdict moderate(String content) {
        if (content == null || content.isEmpty()) {
            return ModerationVerdict.ok();
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (normalized.contains(word)) {
                log.info("[MODERATION:local] decision=BLOCK len={}", content.length());
                return ModerationVerdict.blocked("内容包含不允许的词汇");
            }
        }
        log.info("[MODERATION:local] decision=PASS len={}", content.length());
        return ModerationVerdict.ok();
    }
}
