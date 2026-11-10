/**
 * 操作审计动作中文标签（CR-215）。
 *
 * 与 `common.audit.AuditService.record(...)` 第二个参数一一对应；新增审计动作时同步补这里。
 * 未知动作不隐藏——页面按 `AUDIT_ACTION_LABELS[action] ?? action` 原样显示，避免"记了却看不见"。
 */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  ACCOUNT_REGISTERED: '注册',
  ACCOUNT_DEACTIVATE: '注销申请',
  ACCOUNT_REACTIVATE: '撤销注销',
  ACCOUNT_ANONYMIZE: '注销匿名化',
  PROFILE_UPDATE: '更新资料',
  AVATAR_UPDATE: '更新头像',
  USER_BLOCK: '拉黑用户',
  USER_UNBLOCK: '取消拉黑',
  POST_UPDATE: '编辑帖子',
  POST_DELETE: '删除帖子',
  POST_REMOVE: '下架帖子',
  POST_RESTORE: '恢复帖子',
  REPLY_REMOVE: '下架楼层',
  REPLY_RESTORE: '恢复楼层',
  REPORT_SUBMIT: '提交举报',
  REPORT_CLAIM: '认领工单',
  REPORT_CLOSE: '处理工单',
  ROSTER_IMPORT: '名册导入',
  TAG_DELETE: '删除标签',
  USER_BAN: '封禁用户',
  USER_UNBAN: '解封用户',
  USER_WARN: '警告用户',
  USER_MUTE: '禁言用户',
  USER_UNMUTE: '解除禁言',
  USER_ROLE_UPDATE: '变更角色',
}

/** 动作筛选项（可搜索下拉的候选集；仍允许手动输入未收录动作） */
export const AUDIT_ACTIONS = Object.keys(AUDIT_ACTION_LABELS)

/**
 * 审计目标类型标签（CR-216）。
 *
 * 库里并存两套命名：管理动作写小写复数（`users` / `posts` / `replies`），
 * 举报提交 / 处理直接落举报目标类型的**大写单数**（`POST` / `REPLY` / `USER`）。
 * 两者按字面精确匹配，页面不合并、不改写；未收录值原样显示。
 */
export const AUDIT_TARGET_LABELS: Record<string, string> = {
  users: '用户',
  posts: '帖子',
  replies: '楼层',
  tags: '标签',
  student_roster: '学籍名册',
  POST: '举报对象 · 帖子',
  REPLY: '举报对象 · 楼层',
  USER: '举报对象 · 用户',
}

/** 目标类型筛选项（可搜索下拉的候选集；仍允许手动输入未收录值） */
export const AUDIT_TARGET_TYPES = Object.keys(AUDIT_TARGET_LABELS)
