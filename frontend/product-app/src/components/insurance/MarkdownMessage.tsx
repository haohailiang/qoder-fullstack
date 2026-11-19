/**
 * 保险咨询机器人回答的 Markdown 渲染组件。
 *
 * 设计要点：
 * - 基于 react-markdown + remark-gfm/remark-breaks，输出纯 React 元素，不使用 dangerouslySetInnerHTML；
 * - 不启用 rehype-raw：原始 HTML 只会被当作纯文本展示，不会被浏览器解析执行（XSS 兜底）；
 * - URL 统一经过 defaultUrlTransform 过滤（javascript: 等协议会被清空），外链固定 target/rel；
 * - 代码块、表格外层包裹横向滚动容器，窄屏不撑破抽屉；
 * - 颜色全部取自 antd token，自动跟随亮色/暗色主题。
 */
import {
  createContext,
  memo,
  useContext,
  type ComponentPropsWithoutRef,
  type PropsWithChildren,
} from 'react'
import ReactMarkdown, { defaultUrlTransform, type Components } from 'react-markdown'
import remarkBreaks from 'remark-breaks'
import remarkGfm from 'remark-gfm'
import { theme } from 'antd'

/** 插件列表保持模块级引用，避免每次渲染重新解析插件 */
const REMARK_PLUGINS = [remarkGfm, remarkBreaks]

const MONO_FONT = 'ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace'

/** 流式输出时跟在正文末尾的光标 */
export const STREAMING_CARET = '▍'

/** pre 内部渲染 code 时置为 true，用于区分行内代码与围栏代码块（react-markdown v10 不再提供 inline 标记） */
const InCodeBlockContext = createContext(false)

const HEADING_STYLE: Record<number, { fontSize: number; marginTop: number }> = {
  1: { fontSize: 18, marginTop: 4 },
  2: { fontSize: 16, marginTop: 4 },
  3: { fontSize: 15, marginTop: 2 },
  4: { fontSize: 14, marginTop: 0 },
  5: { fontSize: 13, marginTop: 0 },
  6: { fontSize: 13, marginTop: 0 },
}

function makeHeading(level: 1 | 2 | 3 | 4 | 5 | 6) {
  const Tag = `h${level}` as 'h1'
  const { fontSize, marginTop } = HEADING_STYLE[level]
  return function MarkdownHeading({ children }: PropsWithChildren) {
    const { token } = theme.useToken()
    return (
      <Tag
        style={{
          margin: 0,
          marginTop,
          fontSize,
          fontWeight: 600,
          lineHeight: 1.4,
          color: token.colorText,
        }}
      >
        {children}
      </Tag>
    )
  }
}

function MarkdownParagraph({ children }: PropsWithChildren) {
  return <p style={{ margin: 0, lineHeight: 1.75 }}>{children}</p>
}

function MarkdownList({ children }: PropsWithChildren) {
  return (
    <ul style={{ margin: 0, paddingInlineStart: 22, lineHeight: 1.75 }}>{children}</ul>
  )
}

function MarkdownOrderedList({ children }: PropsWithChildren) {
  return (
    <ol style={{ margin: 0, paddingInlineStart: 22, lineHeight: 1.75 }}>{children}</ol>
  )
}

function MarkdownListItem({ children }: PropsWithChildren) {
  return <li style={{ marginBottom: 2 }}>{children}</li>
}

function MarkdownBlockquote({ children }: PropsWithChildren) {
  const { token } = theme.useToken()
  return (
    <blockquote
      style={{
        margin: 0,
        padding: '4px 12px',
        borderLeft: `3px solid ${token.colorPrimaryBorder}`,
        background: token.colorFillQuaternary,
        borderRadius: '0 4px 4px 0',
        color: token.colorTextSecondary,
      }}
    >
      {children}
    </blockquote>
  )
}

function MarkdownHr() {
  const { token } = theme.useToken()
  return (
    <hr
      style={{
        margin: '4px 0',
        border: 0,
        borderTop: `1px solid ${token.colorBorderSecondary}`,
      }}
    />
  )
}

/** GFM 表格列对齐：把 hast 的 align 收敛到 CSS textAlign 支持的值 */
function tableAlign(align?: string): 'left' | 'center' | 'right' {
  return align === 'center' || align === 'right' ? align : 'left'
}

function MarkdownTable({ children }: PropsWithChildren) {
  return (
    <div
      style={{
        maxWidth: '100%',
        minWidth: 0,
        overflowX: 'auto',
        WebkitOverflowScrolling: 'touch',
      }}
    >
      <table
        style={{
          borderCollapse: 'collapse',
          minWidth: '100%',
          fontSize: 13,
          lineHeight: 1.6,
        }}
      >
        {children}
      </table>
    </div>
  )
}

function MarkdownTableHeadCell({ children, align }: ComponentPropsWithoutRef<'th'>) {
  const { token } = theme.useToken()
  return (
    <th
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorFillTertiary,
        padding: '6px 10px',
        textAlign: tableAlign(align),
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </th>
  )
}

function MarkdownTableCell({ children, align }: ComponentPropsWithoutRef<'td'>) {
  const { token } = theme.useToken()
  return (
    <td
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        padding: '6px 10px',
        textAlign: tableAlign(align),
        verticalAlign: 'top',
      }}
    >
      {children}
    </td>
  )
}

function MarkdownLink({ href, children, title }: ComponentPropsWithoutRef<'a'>) {
  const { token } = theme.useToken()
  return (
    <a
      href={href}
      title={title}
      target="_blank"
      rel="noopener noreferrer"
      style={{ color: token.colorLink, textDecoration: 'underline', wordBreak: 'break-all' }}
    >
      {children}
    </a>
  )
}

function MarkdownImage({ src, alt, title }: ComponentPropsWithoutRef<'img'>) {
  return (
    // 图片可能来自条款文档中的外链，强制不发送 Referer，降低追踪风险
    <img
      src={src}
      alt={alt || ''}
      title={title}
      loading="lazy"
      referrerPolicy="no-referrer"
      style={{ maxWidth: '100%', borderRadius: 6 }}
    />
  )
}

function MarkdownPre({ children }: PropsWithChildren) {
  const { token } = theme.useToken()
  return (
    <pre
      style={{
        margin: 0,
        maxWidth: '100%',
        minWidth: 0,
        overflowX: 'auto',
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 6,
        background: token.colorBgElevated,
      }}
    >
      <InCodeBlockContext.Provider value={true}>{children}</InCodeBlockContext.Provider>
    </pre>
  )
}

function MarkdownCode({ className, children }: ComponentPropsWithoutRef<'code'>) {
  const { token } = theme.useToken()
  const inCodeBlock = useContext(InCodeBlockContext)

  if (inCodeBlock) {
    return (
      <code
        className={className}
        style={{
          display: 'block',
          boxSizing: 'border-box',
          width: 'max-content',
          minWidth: '100%',
          padding: '10px 12px',
          fontFamily: MONO_FONT,
          fontSize: 12,
          lineHeight: 1.65,
          whiteSpace: 'pre',
          color: token.colorText,
        }}
      >
        {children}
      </code>
    )
  }

  return (
    <code
      style={{
        fontFamily: MONO_FONT,
        fontSize: '0.92em',
        padding: '1px 5px',
        borderRadius: 4,
        border: `1px solid ${token.colorBorderSecondary}`,
        background: token.colorFillTertiary,
        wordBreak: 'break-word',
      }}
    >
      {children}
    </code>
  )
}

/** 组件映射为模块级常量，保证引用稳定：流式追加时只更新文本节点，不会整棵树卸载重建 */
const MARKDOWN_COMPONENTS: Components = {
  h1: makeHeading(1),
  h2: makeHeading(2),
  h3: makeHeading(3),
  h4: makeHeading(4),
  h5: makeHeading(5),
  h6: makeHeading(6),
  p: MarkdownParagraph,
  ul: MarkdownList,
  ol: MarkdownOrderedList,
  li: MarkdownListItem,
  blockquote: MarkdownBlockquote,
  hr: MarkdownHr,
  table: MarkdownTable,
  th: MarkdownTableHeadCell,
  td: MarkdownTableCell,
  a: MarkdownLink,
  img: MarkdownImage,
  pre: MarkdownPre,
  code: MarkdownCode,
}

export interface MarkdownMessageProps {
  /** Markdown 源码（流式场景传入已累加的内容） */
  content: string
  /** 流式中：在末尾追加光标，未闭合语法随内容增长自然重解析 */
  streaming?: boolean
  className?: string
}

function MarkdownMessage({ content, streaming, className }: MarkdownMessageProps) {
  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        gap: 8,
        minWidth: 0,
        maxWidth: '100%',
        // 覆盖气泡上的 pre-wrap：Markdown 需要按元素排版，保留原文空白会破坏列表/代码块
        whiteSpace: 'normal',
        wordBreak: 'break-word',
        lineHeight: 1.75,
      }}
    >
      <ReactMarkdown
        remarkPlugins={REMARK_PLUGINS}
        components={MARKDOWN_COMPONENTS}
        urlTransform={defaultUrlTransform}
      >
        {streaming ? content + STREAMING_CARET : content}
      </ReactMarkdown>
    </div>
  )
}

export default memo(MarkdownMessage)
