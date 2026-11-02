/**
 * 草稿箱（localStorage 版）。
 *
 * 以 boardCode 为唯一标识，每版块最多保留一份草稿；全局上限 MAX_DRAFTS 条，
 * 超限时淘汰最旧。localStorage 操作包在 try-catch 中；保存返回实际结果，供页面反馈失败。
 */

export interface Draft {
  boardCode: string
  title: string
  contentMd: string
  updatedAt: string // ISO timestamp
}

const STORAGE_KEY = 'cl_draft'
const MAX_DRAFTS = 10

export function useDraft() {
  function getAllDrafts(): Draft[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return []
      const parsed: unknown = JSON.parse(raw)
      if (!Array.isArray(parsed)) return []
      // 逐项校验必要字段，过滤掉脏数据（非对象 / 缺字段 / 字段类型不符）
      return parsed.filter((d): d is Draft =>
        d != null &&
        typeof d === 'object' &&
        typeof (d as Draft).boardCode === 'string' &&
        typeof (d as Draft).title === 'string' &&
        typeof (d as Draft).contentMd === 'string' &&
        typeof (d as Draft).updatedAt === 'string',
      )
    } catch {
      return []
    }
  }

  function persist(drafts: Draft[]): boolean {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts))
      return true
    } catch {
      return false
    }
  }

  function saveDraft(draft: Draft): boolean {
    let drafts = getAllDrafts()

    // 以 boardCode 为唯一标识：已有则替换，否则新增
    const idx = drafts.findIndex((d) => d.boardCode === draft.boardCode)
    if (idx >= 0) {
      drafts[idx] = draft
    } else {
      drafts.push(draft)
    }

    // 按 updatedAt 降序排列
    drafts.sort((a, b) => (a.updatedAt > b.updatedAt ? -1 : 1))

    // 超过上限则淘汰最旧
    if (drafts.length > MAX_DRAFTS) {
      drafts = drafts.slice(0, MAX_DRAFTS)
    }

    // 写入；QuotaExceededError 时再淘汰一条重试
    if (!persist(drafts)) {
      drafts = drafts.slice(0, Math.max(1, drafts.length - 1))
      return persist(drafts)
    }
    return true
  }

  function loadDraft(boardCode: string): Draft | null {
    const drafts = getAllDrafts()
    return drafts.find((d) => d.boardCode === boardCode) ?? null
  }

  function clearDraft(boardCode: string): void {
    const drafts = getAllDrafts().filter((d) => d.boardCode !== boardCode)
    persist(drafts)
  }

  function listDrafts(): Draft[] {
    return getAllDrafts().sort((a, b) => (a.updatedAt > b.updatedAt ? -1 : 1))
  }

  return { saveDraft, loadDraft, clearDraft, listDrafts }
}
