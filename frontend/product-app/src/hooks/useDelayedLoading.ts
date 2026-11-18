import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 防闪烁阈值：异步任务持续超过该时长后才展示 loading。
 * 快速返回的请求不展示，避免加载态一闪而过。
 */
export const LOADING_DELAY_MS = 200

export interface DelayedLoading {
  /** 是否展示 loading（延迟后为 true，用于绑定 UI） */
  visible: boolean
  begin: () => void
  end: () => void
  /** 包裹异步任务，成功/失败/超时/取消都会在 finally 中关闭 loading */
  wrap: <T>(task: () => Promise<T>) => Promise<T>
}

/**
 * 引用计数 + 延迟可见的 loading 控制器（与 base-app 的 useDelayedLoading 语义一致）。
 */
export function useDelayedLoading(delay = LOADING_DELAY_MS): DelayedLoading {
  const [visible, setVisible] = useState(false)
  const visibleRef = useRef(false)
  const countRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  const updateVisible = useCallback((next: boolean) => {
    visibleRef.current = next
    setVisible(next)
  }, [])

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const begin = useCallback(() => {
    countRef.current += 1
    if (timerRef.current === null && !visibleRef.current) {
      timerRef.current = setTimeout(() => {
        timerRef.current = null
        if (mountedRef.current && countRef.current > 0) {
          updateVisible(true)
        }
      }, delay)
    }
  }, [delay, updateVisible])

  const end = useCallback(() => {
    countRef.current = Math.max(0, countRef.current - 1)
    if (countRef.current === 0) {
      clearTimer()
      if (mountedRef.current) updateVisible(false)
    }
  }, [clearTimer, updateVisible])

  const wrap = useCallback(
    async <T,>(task: () => Promise<T>): Promise<T> => {
      begin()
      try {
        return await task()
      } finally {
        end()
      }
    },
    [begin, end],
  )

  return { visible, begin, end, wrap }
}
