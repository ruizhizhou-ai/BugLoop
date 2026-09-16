/**
 * 本文件验证受保护 Markdown 图片在交给预览组件前会被替换为带鉴权下载得到的 Blob 地址。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import ProtectedMarkdownPreview from '../ProtectedMarkdownPreview.vue'

// vi.mock 会提升执行，下载桩也必须在提升阶段创建，避免模块初始化时出现暂时性死区。
const { downloadFile } = vi.hoisted(() => ({
  downloadFile: vi.fn<(path: string) => Promise<{ blob: Blob; fileName: string | null }>>(),
}))

vi.mock('md-editor-v3', () => ({
  MdPreview: {
    props: { modelValue: { type: String, required: true } },
    template: '<div class="md-preview" :data-markdown="modelValue">{{ modelValue }}</div>',
  },
}))

vi.mock('@/shared/api/http', () => ({ downloadFile }))

describe('ProtectedMarkdownPreview', () => {
  beforeEach(() => {
    downloadFile.mockReset()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:bug-draft-image-3'),
      revokeObjectURL: vi.fn(),
    })
  })

  /** Markdown 正文图片必须通过二进制客户端下载，不能让原生 img 请求遗漏 Authorization。 */
  it('应将受保护正文图片替换为带鉴权下载的 Blob 地址', async () => {
    downloadFile.mockResolvedValue({ blob: new Blob(['image']), fileName: null })
    const wrapper = mount(ProtectedMarkdownPreview, {
      props: { modelValue: '第一步： ![截图](/api/bug-draft-images/3/content)' },
    })

    await flushPromises()

    expect(downloadFile).toHaveBeenCalledWith('/bug-draft-images/3/content')
    expect(wrapper.get('.md-preview').attributes('data-markdown')).toBe(
      '第一步： ![截图](blob:bug-draft-image-3)',
    )
  })

  /** 非草稿图片不需要鉴权代理，应保持原始地址，避免影响已有外链图片。 */
  it('不应改写普通图片地址', async () => {
    const markdown = '![logo](https://example.com/logo.png)'
    const wrapper = mount(ProtectedMarkdownPreview, { props: { modelValue: markdown } })

    await flushPromises()

    expect(downloadFile).not.toHaveBeenCalled()
    expect(wrapper.get('.md-preview').attributes('data-markdown')).toBe(markdown)
  })
})
