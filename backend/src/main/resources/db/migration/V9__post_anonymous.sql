-- V9 · 匿名发帖（F-FORUM-010 / CR-139）
--
-- 设计口径（方案 §7 D1 / D3 / D4）：
--   * 真实 author_id 仍保留（通知 / 审计 / 管理处置 / 我的帖子 / 编辑删除都依赖它）；
--   * is_anonymous 只是**公开展示**开关：1 = 列表 / 搜索 / 详情显示「匿名用户」；
--   * 匿名帖不进他人主页时间线、关注 Feed，不计入公开帖子数 / 获赞数 / 声望；
--   * 附加列 + DEFAULT 0，既有行无需回填，零数据改写。
--
-- 回滚：代码回退本 CR 后该列无害；确需清理时另开前向迁移 DROP COLUMN，不修改本文件。
ALTER TABLE posts
    ADD COLUMN is_anonymous TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '匿名发帖（1=公开展示为匿名用户，真实 author_id 仍保留）' AFTER author_id;
