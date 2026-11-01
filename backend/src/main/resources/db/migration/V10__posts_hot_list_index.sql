-- 热榜列表复合索引（CR-180）：status / is_deleted 等值过滤 + hot_score / created_at / id 排序。
-- 现有 idx_posts_latest 只能做前两列过滤，10 万帖下 ORDER BY hot_score 会 filesort 约 5 万行。
CREATE INDEX idx_posts_hot_list
    ON posts (status, is_deleted, hot_score DESC, created_at DESC, id DESC);
