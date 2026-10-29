import { del, get, post, put } from './client'

/** 与后端 web/vo 一一对应（设计 §3），字段名即接口契约，勿改名 */
export interface BoardVo {
  code: string
  name: string
  description: string
  type: string
  sort: number
}

export interface PageVo<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface PostSummaryVo {
  id: number
  boardCode: string
  boardName: string
  title: string
  authorNickname: string
  /** 默认头像（CR-162）：匿名帖恒为 null，前端回落昵称首字 */
  authorAvatarUrl: string | null
  replyCount: number
  likeCount: number
  /** 阅读数（CR-074）：详情读取去重计数（每帖每账号每日 +1），列表展示用 */
  viewCount: number
  /** 封面图（CR-074）：发布时从正文提取的首张 https 图；无图帖为空，前端不占位 */
  coverUrl?: string
  /** 标签（CR-083）：0~5 个，发帖时规范化；无标签帖为空数组 */
  tags: string[]
  /** 服务端已去 Markdown 并截断为 120 字（设计 §3.2），前端不再兜底截断 */
  summary: string
  createdAt: string
  /** 已有最佳答案（F-QA-001），列表页 [已采纳] 徽标 */
  accepted: boolean
  /** 当前登录账号是否已赞（CR-080）：口径同详情——登录为真实状态、匿名恒 false */
  likedByMe: boolean
  /** 匿名帖（F-FORUM-010 / CR-139）：true 时作者名恒为「匿名用户」，不给主页链接 */
  anonymous: boolean
}

export interface PostDetailVo {
  id: number
  boardCode: string
  boardName: string
  /** QUESTION = 技术问答帖（提问），其余为讨论帖 */
  boardType: string
  title: string
  /** 服务端渲染并净化后的 HTML，前端直接 v-html（XSS 防线在 MarkdownRenderer） */
  contentHtml: string
  /** 匿名帖对非作者恒为 null（CR-139）：前端据此隐藏主页链接 / 关注 / 私信入口 */
  authorId: number | null
  authorNickname: string
  /** 默认头像（CR-162）：匿名帖恒为 null */
  authorAvatarUrl: string | null
  replyCount: number
  likeCount: number
  /** 阅读数（CR-074） */
  viewCount: number
  /** 标签（CR-083） */
  tags: string[]
  accepted: boolean
  /** 当前登录用户是否已点赞（F-FORUM-005）；匿名请求恒 false */
  likedByMe: boolean
  /** 当前登录用户是否已收藏（F-FORUM-005）；匿名请求恒 false */
  favoritedByMe: boolean
  createdAt: string
  /** 匿名帖（F-FORUM-010 / CR-139） */
  anonymous: boolean
}

export interface ReplyVo {
  id: number
  /** 楼层 = 回复序号（第一条回复为 1 楼，帖子本体不占楼层号，设计 §4.1） */
  floorNo: number
  contentHtml: string
  authorId: number
  authorNickname: string
  /** 默认头像（CR-162）：未回填 / 查不到时 null，前端回落昵称首字 */
  authorAvatarUrl: string | null
  /** 楼层点赞数（后端 ReplyVo 一直下发；CR-167 类型检查补齐） */
  likeCount: number
  /** 最佳答案（F-QA-001）；楼层列表按 is_accepted DESC 排序，最佳答案自然置顶 */
  accepted: boolean
  /** 当前登录用户是否已点赞该楼层（F-FORUM-005）；匿名请求恒 false */
  likedByMe: boolean
  /**
   * 被引用楼层摘要（F-009 / CR-085）：服务端组装的楼层号 + 昵称 + 120 字纯文本摘要。
   * 无引用、或被引楼层已不可见（下架 / 已删除）时为 null——null 即不渲染引用块。
   */
  quoted?: QuotedReplyVo | null
  createdAt: string
}

/** 被引用楼层摘要（F-009 / CR-085）；excerpt 服务端已去 Markdown 并截断，前端不二次处理 */
export interface QuotedReplyVo {
  replyId: number
  floorNo: number
  authorNickname: string
  excerpt: string
}

export const listBoards = () => get<BoardVo[]>('/boards')

/** 全站列表排序（F-FORUM-003）：latest = created_at DESC（缺省）；hot = hot_score DESC（热榜，CR-058） */
export type PostSort = 'latest' | 'hot'

/** 列表内容筛选（CR-142「等你来答」）：unanswered = 问答帖且未采纳；缺省不筛。非法值后端 400 / 1001 */
export type PostFilter = 'unanswered'

/**
 * boardCode 缺省即全站最新；分页 page 从 1 起、size 上限 100（设计 §3.2）；
 * 非法 sort / filter 后端 400 / 1001（不静默归一）
 */
export const listPosts = (
  page = 1,
  size = 20,
  boardCode?: string,
  sort: PostSort = 'latest',
  filter?: PostFilter,
) => {
  const query = new URLSearchParams({ page: String(page), size: String(size), sort })
  if (boardCode) {
    query.set('boardCode', boardCode)
  }
  if (filter) {
    query.set('filter', filter)
  }
  return get<PageVo<PostSummaryVo>>(`/posts?${query.toString()}`)
}

export const getPost = (id: number | string) => get<PostDetailVo>(`/posts/${id}`)

export const listReplies = (postId: number | string, page = 1, size = 20) =>
  get<PageVo<ReplyVo>>(`/posts/${postId}/replies?page=${page}&size=${size}`)

export const publishPost = (payload: {
  boardCode: string
  title: string
  contentMd: string
  tags?: string[]
  /** 匿名发布（F-FORUM-010 / CR-139）：缺省 false，发布后不可更改 */
  anonymous?: boolean
}) => post<{ id: number }>('/posts', payload)

/** 编辑原文（F-FORUM-007 / CR-128）：仅作者可取，含原始 Markdown 与标签；非作者 403 / 4002、不可见 404 / 3001 */
export interface EditablePostVo {
  id: number
  boardCode: string
  boardName: string
  title: string
  contentMd: string
  tags: string[]
  /** 匿名标记（CR-139）：发布后不可切换，编辑页只读展示 */
  anonymous: boolean
}

export const getEditablePost = (id: number | string) => get<EditablePostVo>(`/posts/${id}/edit`)

/**
 * 编辑自己的帖子（F-FORUM-007 / CR-128）：标题 / 正文 / 标签可改，**版块不可改**；
 * 非作者 403 / 4002、已下架或已删除 404 / 3001、机审命中 422 / 5001。
 */
export const updatePost = (id: number | string, payload: { title: string; contentMd: string; tags?: string[] }) =>
  put<void>(`/posts/${id}`, payload)

/**
 * 删除自己的帖子（仅作者，软删，F-FORUM-006 / CR-065）：
 * 成功 200；非作者 403 / 4002；未登录 401 / 4001；二次删除幂等 404 / 3001。
 */
export const deletePost = (id: number | string) => del<void>(`/posts/${id}`)

/**
 * 回帖（需登录）：quotedReplyId 为引用的目标楼层（F-009 / CR-085），可空；
 * 目标不存在 / 跨帖时后端 404 / 3001（不区分原因，防按 id 探测）。
 */
export const publishReply = (postId: number | string, contentMd: string, quotedReplyId?: number) =>
  post<{ id: number; floorNo: number }>(`/posts/${postId}/replies`, { contentMd, quotedReplyId })

/** 采纳最佳答案（仅提问者）：可更换（后一次覆盖前一次），不能采纳自己的回复（3003） */
export const acceptReply = (postId: number | string, replyId: number) =>
  post<void>(`/posts/${postId}/accept`, { replyId })

/** 点赞 / 取消点赞帖子（toggle，F-FORUM-005）：active 为操作后状态，count 为最新点赞数 */
export const togglePostLike = (postId: number | string) =>
  post<{ active: boolean; count: number }>(`/posts/${postId}/like`)

/** 收藏 / 取消收藏帖子（toggle，F-FORUM-005） */
export const togglePostFavorite = (postId: number | string) =>
  post<{ active: boolean; count: number }>(`/posts/${postId}/favorite`)

/** 点赞 / 取消点赞楼层（toggle，F-FORUM-005） */
export const toggleReplyLike = (postId: number | string, replyId: number) =>
  post<{ active: boolean; count: number }>(`/posts/${postId}/replies/${replyId}/like`)

/** 我的收藏（需登录）：本人收藏的帖子，按收藏时间倒序（F-FORUM-005） */
export const listMyFavorites = (page = 1, size = 20) =>
  get<PageVo<PostSummaryVo>>(`/favorites/mine?page=${page}&size=${size}`)

/**
 * 「我的帖子」条目（F-ACC-007b）：与全站列表的 `PostSummaryVo` **不同形**——
 * 多一个只面向作者的 `status`，没有互动计数与作者昵称，也**没有 `boardName`**（版块名在前端由 `boardCode` 查表）。
 * `status=REMOVED` 的条目仍返回（作者要知道"少了哪一帖"），但点进详情是 `404 / 3001`，故渲染为不可点。
 */
export interface MyPostSummaryVo {
  id: number
  boardCode: string
  title: string
  summary: string
  /** PUBLISHED / REMOVED（自己删除的不会出现在这里） */
  status: string
  createdAt: string
  /** 匿名标记（CR-139）：本人列表仍可见，仅展示层加标记 */
  anonymous: boolean
}

/** 「我的回帖」条目（F-ACC-007c）：父帖定位三件套缺一即"不知道自己答在哪"。父帖不可见的楼层后端已整条过滤 */
export interface MyReplySummaryVo {
  id: number
  postId: number
  postTitle: string
  floorNo: number
  summary: string
  /** 楼层自身的处置状态；父帖状态不下发到这里 */
  status: string
  createdAt: string
}

/** 我的帖子（需登录）：时间倒序分页，含被平台下架的条目、不含自己删除的（F-ACC-007b） */
export const listMyPosts = (page = 1, size = 20) =>
  get<PageVo<MyPostSummaryVo>>(`/posts/mine?page=${page}&size=${size}`)

/** 我的回帖（需登录）：时间倒序分页，父帖不可见的楼层整条不出现（F-ACC-007c） */
export const listMyReplies = (page = 1, size = 20) =>
  get<PageVo<MyReplySummaryVo>>(`/replies/mine?page=${page}&size=${size}`)

/**
 * 「关注」Feed（CR-074，需登录）：本人关注作者的时间序帖子；未关注任何人时为空页。
 */
export const listFollowingPosts = (page = 1, size = 20) =>
  get<PageVo<PostSummaryVo>>(`/posts/following?page=${page}&size=${size}`)

/** 相似帖子推荐（发帖页，F-FORUM-009）：标题≥6字时后端按 ngram 检索相似帖，最多返回 3 条 */
export interface SimilarPostVo {
  id: number
  title: string
  boardCode: string
  boardName: string
  replyCount: number
  accepted: boolean
  createdAt: string
}

/** 获取相似帖子：title 为当前输入标题（≥6 字），前端最多触发 2 次（服务端未限流） */
export const getSimilarPosts = (title: string) =>
  get<SimilarPostVo[]>(`/posts/similar?title=${encodeURIComponent(title)}`)

/**
 * 站内搜索（公开，F-FORUM-008）：keyword 2~50 字（后端 ngram 分词，单字不受理 → 1001），
 * boardCode / days（仅 7、30、90）可选筛选，按相关度 + 时间排序。
 */
export const searchPosts = (
  keyword: string,
  opts: { boardCode?: string; days?: number; page?: number; size?: number } = {},
) => {
  const query = new URLSearchParams({
    keyword,
    page: String(opts.page ?? 1),
    size: String(opts.size ?? 20),
  })
  if (opts.boardCode) {
    query.set('boardCode', opts.boardCode)
  }
  if (opts.days) {
    query.set('days', String(opts.days))
  }
  return get<PageVo<PostSummaryVo>>(`/posts/search?${query.toString()}`)
}
