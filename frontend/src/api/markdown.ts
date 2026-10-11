import { post } from './client'

/** Markdown 预览（F-012 / CR-087）：服务端渲染 + 净化，前端只负责展示返回的 HTML（ADR-005） */
export const previewMarkdown = (contentMd: string) =>
  post<{ html: string }>('/markdown/preview', { contentMd })
