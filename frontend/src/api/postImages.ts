import { getBinary, upload } from './client'

export const isPostImageUrl = (url: string): boolean =>
  /^\/api\/v1\/post-images\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/content$/.test(url)

export interface UploadedPostImage { id: string; url: string; width: number; height: number; size: number }
export function uploadPostImage(file: File, signal: AbortSignal): Promise<UploadedPostImage> {
  const body = new FormData()
  body.append('file', file)
  return upload('/post-images', body, signal)
}
export const readPostImage = (url: string, signal: AbortSignal): Promise<Blob> =>
  getBinary(url.slice('/api/v1'.length), signal)
