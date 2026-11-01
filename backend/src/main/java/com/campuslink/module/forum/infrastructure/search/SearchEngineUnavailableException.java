package com.campuslink.module.forum.infrastructure.search;

/**
 * 搜索引擎不可用（连接失败 / 索引缺失 / 任务失败）：应用层捕获后回落 MySQL 搜索，不把 500 透给读者。
 */
public class SearchEngineUnavailableException extends RuntimeException {

    public SearchEngineUnavailableException(String message) {
        super(message);
    }

    public SearchEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
