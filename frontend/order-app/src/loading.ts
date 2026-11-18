/**
 * order-app 轻量加载组件（原生 TS + DOM，零额外依赖）。
 *
 * 视觉规格与 Element Plus 对齐：主色 #409eff、20px 圆环、内容水平垂直居中；
 * 颜色通过根节点上的 --oa-* CSS 变量注入，跟随 dark/light 主题切换。
 */

/** 防闪烁阈值：加载持续超过该时长后才展示骨架或遮罩 */
export const LOADING_DELAY = 200

const STYLE_ID = 'order-app-loading-styles'

export interface LoadingOptions {
  /** 加载文案（已按当前语言翻译） */
  text: string
  /** 首屏骨架 HTML，由 tableSkeletonHtml 生成 */
  skeletonHtml: string
}

export interface LoadingOverlay {
  /** skeleton：首屏骨架；overlay：已有数据时的居中遮罩 */
  show: (mode: 'skeleton' | 'overlay') => void
  /** 隐藏并清理骨架，幂等，可重复调用 */
  hide: () => void
  /** 移除节点与定时器 */
  destroy: () => void
}

/** 只注入一次样式表，避免重复渲染时叠加 <style> */
export function ensureLoadingStyles(): void {
  if (document.getElementById(STYLE_ID)) return

  const style = document.createElement('style')
  style.id = STYLE_ID
  style.textContent = `
    .oa-loading-overlay {
      position: absolute;
      inset: 0;
      z-index: 10;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      border-radius: 8px;
      background: var(--oa-overlay-bg, rgba(255, 255, 255, 0.75));
      opacity: 0;
      visibility: hidden;
      pointer-events: none;
      transition: opacity 0.15s ease;
    }
    .oa-loading-overlay.is-visible {
      opacity: 1;
      visibility: visible;
      pointer-events: auto;
    }
    .oa-spinner {
      width: 20px;
      height: 20px;
      border: 2px solid var(--oa-primary, #409eff);
      border-top-color: transparent;
      border-radius: 50%;
      animation: oa-spin 0.8s linear infinite;
    }
    .oa-loading-text {
      font-size: 13px;
      color: var(--oa-text-secondary, #909399);
    }
    .oa-btn-spinner {
      display: inline-block;
      width: 12px;
      height: 12px;
      margin-right: 6px;
      border: 1.5px solid currentColor;
      border-top-color: transparent;
      border-radius: 50%;
      animation: oa-spin 0.8s linear infinite;
      vertical-align: -1px;
    }
    .oa-skeleton {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 8px 0;
    }
    .oa-skeleton-row {
      display: grid;
      grid-template-columns: 1.6fr 2fr 0.8fr 1fr 1fr 0.8fr;
      gap: 16px;
      align-items: center;
    }
    .oa-skeleton-bar {
      height: 16px;
      border-radius: 4px;
      background-color: var(--oa-skeleton-bg, #f0f2f5);
      background-image: linear-gradient(
        90deg,
        transparent 0%,
        var(--oa-skeleton-highlight, #e4e7ed) 50%,
        transparent 100%
      );
      background-size: 200% 100%;
      animation: oa-shimmer 1.4s ease-in-out infinite;
    }
    @keyframes oa-spin {
      to {
        transform: rotate(360deg);
      }
    }
    @keyframes oa-shimmer {
      0% {
        background-position: 150% 0;
      }
      100% {
        background-position: -50% 0;
      }
    }
  `
  document.head.appendChild(style)
}

/** 生成与订单表格列结构近似的骨架行 */
export function tableSkeletonHtml(rows = 5): string {
  const widths = ['70%', '85%', '60%', '75%', '65%', '70%']
  const rowHtml = widths.map((width) => `<span class="oa-skeleton-bar" style="width:${width}"></span>`).join('')
  const rowsHtml = Array.from({ length: rows }, () => `<div class="oa-skeleton-row">${rowHtml}</div>`).join('')
  return `<div class="oa-skeleton" data-oa-skeleton>${rowsHtml}</div>`
}

/**
 * 创建延迟展示的加载态：
 * - 骨架与遮罩都只在超过 LOADING_DELAY 后出现，快速返回不闪现；
 * - hide() 只移除骨架节点与遮罩可见类，不会清空调用方已渲染的结果。
 */
export function createLoadingOverlay(target: HTMLElement, options: LoadingOptions): LoadingOverlay {
  ensureLoadingStyles()

  const overlay = document.createElement('div')
  overlay.className = 'oa-loading-overlay'
  overlay.setAttribute('role', 'status')
  overlay.setAttribute('aria-live', 'polite')

  const spinner = document.createElement('span')
  spinner.className = 'oa-spinner'
  const text = document.createElement('span')
  text.className = 'oa-loading-text'
  text.textContent = options.text
  overlay.appendChild(spinner)
  overlay.appendChild(text)
  target.appendChild(overlay)

  let timer: number | null = null
  let skeletonShown = false

  function clearTimer() {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  function removeSkeleton() {
    const skeleton = target.querySelector('[data-oa-skeleton]')
    if (skeleton) skeleton.remove()
    skeletonShown = false
  }

  return {
    show(mode) {
      clearTimer()
      // 调用方可能重写过 #order-content 的 innerHTML，导致遮罩节点被移除，这里补回
      if (!target.contains(overlay)) target.appendChild(overlay)
      if (mode === 'skeleton') {
        timer = window.setTimeout(() => {
          timer = null
          if (skeletonShown) return
          target.insertAdjacentHTML('afterbegin', options.skeletonHtml)
          skeletonShown = true
        }, LOADING_DELAY)
        return
      }

      timer = window.setTimeout(() => {
        timer = null
        overlay.classList.add('is-visible')
      }, LOADING_DELAY)
    },
    hide() {
      clearTimer()
      overlay.classList.remove('is-visible')
      removeSkeleton()
    },
    destroy() {
      clearTimer()
      removeSkeleton()
      overlay.remove()
    },
  }
}

/**
 * 按钮级 loading：禁用按钮并在文案前插入内联旋转图标。
 * 与原文案通过 dataset 保存/恢复，不依赖外部状态。
 */
export function withButtonLoading(button: HTMLButtonElement | null, loading: boolean): void {
  if (!button) return

  if (loading) {
    if (button.dataset.oaLoading === '1') return
    button.dataset.oaLoading = '1'
    button.dataset.oaLabel = button.innerHTML
    button.disabled = true
    button.innerHTML = `<span class="oa-btn-spinner"></span>${button.dataset.oaLabel}`
    return
  }

  if (button.dataset.oaLoading !== '1') return
  delete button.dataset.oaLoading
  button.disabled = false
  if (button.dataset.oaLabel) {
    button.innerHTML = button.dataset.oaLabel
    delete button.dataset.oaLabel
  }
}
