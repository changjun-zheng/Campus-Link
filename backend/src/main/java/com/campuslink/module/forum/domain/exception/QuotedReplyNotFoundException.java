package com.campuslink.module.forum.domain.exception;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;

/**
 * 领域异常：被引用楼层不可读（F-009 / CR-085）——不存在 / 已删除 / 已下架 / **不属于本帖**四种情况
 * 一律不区分，统一提示可防止按 id 探测他帖楼层或已处置内容（与 {@link PostNotFoundException} 同思路）。
 *
 * <p>错误码在构造函数内绑定 {@link ResultCode#NOT_FOUND}（3001 / 404）——沿用既有分层惯例：
 * domain 用领域词汇表达失败，ResultCode 仍是错误响应的单点来源，全局出口无需改动。
 */
public class QuotedReplyNotFoundException extends ApiException {

    public QuotedReplyNotFoundException() {
        super(ResultCode.NOT_FOUND);
    }
}
