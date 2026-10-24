-- ============================================================================
-- V7 · 正文全文检索索引（F-FORUM-009 / CR-136）
--
-- 只加索引、不改表结构：posts.content_md 是发布时的 Markdown 原文，正文检索
-- 需要它与 V1 已就位的 ft_posts_title 一样走 ngram 分词（技术方案 ADR-004 /
-- ADR-009）。试点规模由 MySQL 承担，MeiliSearch 仍留作规模升级触发。
--
-- 为什么另建一条索引而不是把 title 与 content_md 合成一条：
-- 搜索相关度按「标题 MATCH × 2 + 正文 MATCH × 1」加权，MySQL 的 MATCH 只能
-- 对同一条 FULLTEXT 索引内的列组合求值；两条独立索引才能分别计分。
--
-- 回滚：V8__revert_cr136.sql（ALTER TABLE posts DROP INDEX ft_posts_content），
-- 索引可重建、无数据丢失。
-- ============================================================================

ALTER TABLE posts
    ADD FULLTEXT KEY ft_posts_content (content_md) WITH PARSER ngram;
