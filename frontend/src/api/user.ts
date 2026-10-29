import { get } from './client'
import type { PageVo, PostSummaryVo } from './forum'

/**
 * 他人主页的公开资料（F-ACC-002 本体最小版），对应后端 `account.web.UserProfileVo`。
 *
 * 与 `types.ts` 的 `UserInfo`（`GET /users/me`，本人视角）**刻意不同形**：那份带
 * `role` / `status` / `verified` 与 email 之外的账号列，这份一律没有——公开资料只有展示四列 + 三个计数。
 * 也**没有 `following`**：那一位需要 viewer，而本端点是匿名的；登录态下另调 `api/follow.ts` 的
 * `getFollowState` 取（与帖子详情页同一做法）。
 */
export interface UserProfileVo {
  id: number
  nickname: string
  /** 默认头像（CR-163）：匿名化 / 历史空值为 null，前端回落昵称首字 */
  avatarUrl?: string | null
  /** 专业（学籍名册导入 / 本人可改）；未填为 null */
  major: string | null
  /** 个性签名；未填为 null */
  bio: string | null
  /** 加入时间，ISO-8601 UTC */
  createdAt: string
  /** 入学年级（CR-145 届徽章）：注册时快照名册 grade 归一后的 4 位年份；null 不渲染徽章 */
  grade: string | null
  postCount: number
  /** 可见回帖数（CR-138）：与主页回帖时间线的 total 同源 */
  replyCount: number
  /** 获赞数（CR-104 / D-1）：其**可见帖子与可见楼层**收到的赞之和 */
  likeCount: number
  /** 被采纳数（CR-104 / D-2）：其回答被采纳为最佳答案的次数 */
  acceptedCount: number
  /** 声望（CR-138）：被采纳 ×10 + 获赞 ×2 + 发帖 ×1 + 回帖 ×1，只统计可见内容 */
  reputation: number
  /** 社区等级档位 1~6（CR-138） */
  level: number
  /** 档位名称：初入 / 学徒 / 常客 / 贡献者 / 资深成员 / 社区之星 */
  levelName: string
  /** 下一档的声望门槛；**满级为 null**（不渲染升级进度文案） */
  nextLevelAt: number | null
  /** 当前档位内的进度百分比 0~100 */
  levelProgress: number
  followerCount: number
  followingCount: number
}

/**
 * 他人主页的公开楼层条目（CR-104），对应后端 `forum.web.vo.PublicReplyVo`。
 *
 * 与 `MyReplyVo`（`/replies/mine`，本人视角）**唯一形状差别是没有 `status`**：路人视角下不可见楼层
 * 已在服务端 SQL 整条滤掉，下发这一列等于把处置事实交给每个匿名访客。同样没有原文 `contentMd`，
 * 只有服务端截断（120 字上限）后的 `summary`。
 */
export interface PublicReplyVo {
  id: number
  /** 父帖定位：缺 postId 与标题就只知道「答过一层」而答不出「答在哪」 */
  postId: number
  postTitle: string
  floorNo: number
  summary: string
  createdAt: string
}

/** 用户公开资料（公开端点，匿名可读）；用户不存在或已注销 → 404 / 2007 */
export const getUserProfile = (id: number | string) => get<UserProfileVo>(`/users/${id}`)

/**
 * 某用户的公开帖子（公开端点，匿名可读）：时间倒序分页，出参与全站列表**同形**
 * （`PostSummaryVo`），故主页时间线直接复用 `PostListItem` 组件。
 *
 * 可见性口径同全站（`PUBLISHED` 且未删除）：已下架与已删除的帖一律不出现，与「我的帖子」相反。
 */
export const getUserPosts = (id: number | string, page = 1, size = 20) =>
  get<PageVo<PostSummaryVo>>(`/users/${id}/posts?page=${page}&size=${size}`)

/**
 * 某用户的公开回帖时间线（公开端点，匿名可读，CR-104）：时间倒序分页。
 *
 * 口径与 `getUserPosts` 一致（路人视角，被处置的内容不出现），与「我的回帖」相反（那条要露出被下架的楼层）。
 */
export const getUserReplies = (id: number | string, page = 1, size = 20) =>
  get<PageVo<PublicReplyVo>>(`/users/${id}/replies?page=${page}&size=${size}`)
