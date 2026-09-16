<!--
本文件统一渲染包含受保护正文图片的 Markdown。
图片地址仍保存为稳定的后端 API 路径；组件通过带 Authorization 请求头的二进制客户端获取 Blob，避免原生 img 请求遗漏登录凭证。
-->
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { downloadFile } from '@/shared/api/http'

interface ProtectedMarkdownPreviewProps {
  /** 需要按 Markdown 语法渲染的正文。 */
  modelValue: string
}

const props = defineProps<ProtectedMarkdownPreviewProps>()

const DRAFT_IMAGE_PATH = /^\/api\/bug-draft-images\/\d+\/content$/
const DRAFT_IMAGE_PATH_IN_MARKDOWN = /\/api\/bug-draft-images\/\d+\/content/g
const imageBlobUrls = new Map<string, string>()
const imageLoadTasks = new Map<string, Promise<string>>()
const renderedMarkdown = ref('')
const imageLoadFailed = ref(false)
let renderVersion = 0

/**
 * 为 Markdown 渲染器转换正文图片地址；非本功能的外部图片保持原地址，不改变既有 Markdown 行为。
 *
 * @param url Markdown 图片原始地址
 * @return 已鉴权下载后的 Blob URL，或无需处理的原始地址
 */
async function transformImageUrl(url: string): Promise<string> {
  if (!DRAFT_IMAGE_PATH.test(url)) {
    return url
  }
  const cached = imageBlobUrls.get(url)
  if (cached) {
    return cached
  }
  const pending = imageLoadTasks.get(url)
  if (pending) {
    return pending
  }

  // 二进制客户端自身已有 /api 基路径，持久化 URL 需去掉该前缀以避免请求变成 /api/api/...。
  const task = downloadFile(url.slice('/api'.length))
    .then(({ blob }) => {
      const blobUrl = URL.createObjectURL(blob)
      imageBlobUrls.set(url, blobUrl)
      return blobUrl
    })
    .finally(() => imageLoadTasks.delete(url))
  imageLoadTasks.set(url, task)
  return task
}

/**
 * 将正文中所有受保护图片替换为已鉴权获取的 Blob 地址，再交给 MdPreview 渲染。
 * MdPreview 不会使用 transformImgUrl 属性，因此必须在传入预览器前完成地址转换。
 *
 * @param markdown 原始 Markdown 正文
 */
async function refreshRenderedMarkdown(markdown: string): Promise<void> {
  const currentVersion = ++renderVersion
  const imageUrls = [...new Set(markdown.match(DRAFT_IMAGE_PATH_IN_MARKDOWN) ?? [])]
  if (imageUrls.length === 0) {
    imageLoadFailed.value = false
    renderedMarkdown.value = markdown
    return
  }

  try {
    const replacements = await Promise.all(
      imageUrls.map(async (url) => [url, await transformImageUrl(url)] as const),
    )
    // Markdown 在等待请求返回期间可能已更新；旧请求不能覆盖最新正文。
    if (currentVersion !== renderVersion) {
      return
    }
    imageLoadFailed.value = false
    renderedMarkdown.value = replacements.reduce(
      // 项目编译目标未提供 String.replaceAll，使用 split/join 兼容替换正文中的全部相同地址。
      (content, [sourceUrl, blobUrl]) => content.split(sourceUrl).join(blobUrl),
      markdown,
    )
  } catch {
    if (currentVersion !== renderVersion) {
      return
    }
    // 下载失败时保留原始正文，至少让用户能看到图片的替代文本并获知加载异常。
    imageLoadFailed.value = true
    renderedMarkdown.value = markdown
  }
}

watch(
  () => props.modelValue,
  (markdown) => {
    void refreshRenderedMarkdown(markdown)
  },
  { immediate: true },
)

/** 组件卸载时释放所有图片 Blob URL，避免详情反复打开造成浏览器内存累积。 */
onBeforeUnmount(() => {
  imageBlobUrls.forEach((url) => URL.revokeObjectURL(url))
  imageBlobUrls.clear()
})
</script>

<template>
  <p v-if="imageLoadFailed" class="protected-markdown-preview__warning">
    部分正文图片加载失败，请刷新页面后重试。
  </p>
  <md-preview
    :model-value="renderedMarkdown"
    preview-theme="github"
  />
</template>

<style scoped>
.protected-markdown-preview__warning {
  margin: 0 0 12px;
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>
