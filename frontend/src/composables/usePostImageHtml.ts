import { nextTick, onScopeDispose, ref, watch, type Ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { isPostImageUrl, readPostImage } from '../api/postImages'

/** Only transforms image sources in already sanitized server HTML; never renders Markdown. */
export function usePostImageHtml(source: Readonly<Ref<string>>) {
  const html = ref('')
  const auth = useAuthStore()
  let generation = 0
  let cancel: AbortController | undefined
  let urls: string[] = []
  const release = (items: string[]) => items.forEach((url) => URL.revokeObjectURL(url))
  watch([source, () => auth.token], async ([value], _, onCleanup) => {
    const current = ++generation
    cancel?.abort()
    const controller = new AbortController()
    cancel = controller
    onCleanup(() => controller.abort())
    const previous = urls
    urls = []
    html.value = ''
    await nextTick()
    release(previous)
    // A template is inert: managed URLs never enter the live DOM before JWT reads finish.
    const template = document.createElement('template')
    template.innerHTML = value
    const managed = [...template.content.querySelectorAll<HTMLImageElement>('img[src]')]
      .filter((img) => isPostImageUrl(img.getAttribute('src') || ''))
    const created: string[] = []
    const pending = new Map<string, Promise<string>>()
    await Promise.all(managed.map(async (img) => {
      const canonical = img.getAttribute('src')!
      img.removeAttribute('src')
      let fetched = pending.get(canonical)
      if (!fetched) {
        fetched = readPostImage(canonical, controller.signal).then((blob) => {
          const url = URL.createObjectURL(blob)
          created.push(url)
          return url
        })
        pending.set(canonical, fetched)
      }
      try { img.setAttribute('src', await fetched) } catch { img.setAttribute('alt', '图片暂不可用') }
    }))
    if (current !== generation || controller.signal.aborted) { release(created); return }
    urls = created
    html.value = template.innerHTML
  }, { immediate: true })
  onScopeDispose(() => { generation++; cancel?.abort(); release(urls) })
  return html
}
