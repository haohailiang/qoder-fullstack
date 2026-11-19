/**
 * MarkdownMessage 单元测试
 *
 * 覆盖：
 * - 标题 / 列表 / 表格 / 代码块 / 引用 / 链接 / 分隔线 / 行内代码的 DOM 结构
 * - 原始 HTML 与 javascript: 链接不会被解析执行（XSS 兜底）
 * - 流式追加（未闭合围栏、未闭合加粗）时的增量渲染与最终格式
 */
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import MarkdownMessage from '@/components/insurance/MarkdownMessage'

const SAMPLE = `# 保障范围

本产品覆盖 **住院医疗** 与 *门诊* 费用，详见下表：

| 项目 | 限额 |
| --- | --- |
| 住院 | 300 万 |
| 门诊 | 1 万 |

1. 先报案
2. 再理赔

- 免赔额 1 万
- 等待期 30 天

\`\`\`js
const limit = 3000000
\`\`\`

> 具体以条款为准

行内代码 \`deductible\` 示例。

[查看条款](https://example.com/terms)

---
`

describe('MarkdownMessage', () => {
  it('应把常见 Markdown 语法渲染为对应的富文本结构', () => {
    const { container } = render(<MarkdownMessage content={SAMPLE} />)

    expect(container.querySelector('h1')?.textContent).toBe('保障范围')
    expect(container.querySelector('strong')?.textContent).toBe('住院医疗')
    expect(container.querySelector('em')?.textContent).toBe('门诊')

    // 表格：两列表头 + 两行数据
    expect(container.querySelectorAll('table thead th')).toHaveLength(2)
    expect(container.querySelectorAll('table tbody tr')).toHaveLength(2)
    expect(container.querySelector('table tbody tr td')?.textContent).toBe('住院')

    // 有序 / 无序列表
    expect(container.querySelectorAll('ol > li')).toHaveLength(2)
    expect(container.querySelectorAll('ul > li')).toHaveLength(2)

    // 围栏代码块与行内代码
    const codeBlock = container.querySelector('pre > code')
    expect(codeBlock?.textContent).toContain('const limit = 3000000')
    expect(container.querySelectorAll('p > code')).toHaveLength(1)

    // 引用、分隔线
    expect(container.querySelector('blockquote')?.textContent).toContain('具体以条款为准')
    expect(container.querySelector('hr')).not.toBeNull()

    // 链接：新窗口打开 + 安全 rel
    const link = container.querySelector('a')
    expect(link?.getAttribute('href')).toBe('https://example.com/terms')
    expect(link?.getAttribute('target')).toBe('_blank')
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer')

    // 不再显示原始 Markdown 符号
    expect(container.textContent).not.toContain('| 项目 |')
    expect(container.textContent).not.toContain('```')
  })

  it('原始 HTML 只能作为纯文本展示，不产生可执行节点', () => {
    const malicious = [
      '<script>window.__mdXss = 1</script>',
      '<img src="x" onerror="window.__mdXss = 2">',
      '[危险链接](javascript:window.__mdXss = 3)',
      '<a href="javascript:alert(1)">点我</a>',
    ].join('\n\n')

    const { container } = render(<MarkdownMessage content={malicious} />)

    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img[onerror]')).toBeNull()
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull()
    // 标签本身以文本形式保留，便于用户理解原文
    expect(container.textContent).toContain('<script>')
    expect(container.textContent).toContain('onerror')
    expect((window as unknown as { __mdXss?: number }).__mdXss).toBeUndefined()
  })

  it('流式追加时应逐段渲染，未闭合语法不报错且最终格式正确', () => {
    const { container, rerender } = render(<MarkdownMessage content="## 等待期" streaming />)

    // 流式光标紧跟在已收到的正文后面（此时标题已按 h2 渲染）
    expect(container.querySelector('h2')?.textContent).toBe('等待期▍')

    // 未闭合列表项：内容累加，不抛异常
    rerender(<MarkdownMessage content={'## 等待期\n\n- 首' } streaming />)
    rerender(<MarkdownMessage content={'## 等待期\n\n- 首次投保\n- 续保'} streaming />)
    expect(container.querySelectorAll('li')).toHaveLength(2)

    // 未闭合围栏：按代码块渲染，追加后仍是同一个 pre
    rerender(<MarkdownMessage content={'## 等待期\n\n- 首次投保\n- 续保\n\n```js\nconst a = 1'} streaming />)
    const partialPre = container.querySelector('pre > code')
    expect(partialPre?.textContent).toContain('const a = 1')

    rerender(
      <MarkdownMessage
        content={'## 等待期\n\n- 首次投保\n- 续保\n\n```js\nconst a = 1\nconst b = 2\n```'}
      />,
    )
    const finalPre = container.querySelector('pre > code')
    expect(finalPre?.textContent).toContain('const a = 1')
    expect(finalPre?.textContent).toContain('const b = 2')
    // 流式结束后光标消失
    expect(container.textContent).not.toContain('▍')
    expect(container.querySelectorAll('li')).toHaveLength(2)
  })

  it('未闭合的加粗标记应作为普通文本展示', () => {
    const { container } = render(<MarkdownMessage content="重点 **未闭合" />)

    expect(container.querySelector('strong')).toBeNull()
    expect(container.textContent).toContain('**未闭合')
  })
})
