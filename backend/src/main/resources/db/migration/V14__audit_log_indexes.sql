-- CR-215 操作审计只读查询：audit_logs 只增不减，后台默认按时间倒序、并常按动作筛选。
-- 既有 idx_audit_actor (actor_id, created_at) 已覆盖「按操作者」；本迁移补默认排序与动作筛选两条。
ALTER TABLE audit_logs
    ADD KEY idx_audit_created (created_at),
    ADD KEY idx_audit_action (action, created_at);
