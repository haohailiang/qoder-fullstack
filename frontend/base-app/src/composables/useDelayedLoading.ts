import { onScopeDispose, ref, watch, type Ref } from 'vue'

/**
 * 防闪烁阈值：异步任务持续超过该时长后才展示 loading。
 * 快速返回的请求不展示，避免加载态一闪而过。
 */
export const LOADING_DELAY_MS = 200

export interface DelayedLoading {
  /** 是否存在进行中的任务（立即变化，仅用于逻辑判断，不要直接绑定 UI） */
  active: Ref<boolean>
  /** 是否展示 loading（延迟后为 true，用于绑定 UI） */
  visible: Ref<boolean>
  begin: () => void
  end: () => void
  /** 包裹异步任务，成功/失败/超时/取消都会在 finally 中关闭 loading */
  wrap: <T>(task: () => Promise<T>) => Promise<T>
}

/**
 * 引用计数 + 延迟可见的 loading 控制器。
 *
 * - 并发任务通过计数合并，最后一个任务结束才隐藏，避免同级区域 loading 闪烁；
 * - `begin` 后启动延时定时器，超过 delay 才把 visible 置为 true；
 * - `end` 计数归零时立即清理定时器并隐藏。
 */
export function useDelayedLoading(delay = LOADING_DELAY_MS): DelayedLoading {
  const active = ref(false)
  const visible = ref(false)
  let count = 0
  let timer: ReturnType<typeof setTimeout> | null = null

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function begin() {
    count += 1
    active.value = true
    if (timer === null && !visible.value) {
      timer = setTimeout(() => {
        timer = null
        if (count > 0) visible.value = true
      }, delay)
    }
  }

  function end() {
    count = Math.max(0, count - 1)
    if (count === 0) {
      clearTimer()
      visible.value = false
      active.value = false
    }
  }

  async function wrap<T>(task: () => Promise<T>): Promise<T> {
    begin()
    try {
      return await task()
    } finally {
      end()
    }
  }

  onScopeDispose(() => {
    clearTimer()
    count = 0
    visible.value = false
    active.value = false
  })

  return { active, visible, begin, end, wrap }
}

/**
 * 由布尔状态派生「延迟可见」的 loading 标记。
 * 用于全局进度条、子应用挂载占位等外部状态源。
 */
export function useDelayedBusy(source: Readonly<Ref<boolean>>, delay = LOADING_DELAY_MS): Ref<boolean> {
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  watch(
    source,
    (busy) => {
      clearTimer()
      if (busy) {
        timer = setTimeout(() => {
          timer = null
          visible.value = true
        }, delay)
      } else {
        visible.value = false
      }
    },
    { immediate: true },
  )

  onScopeDispose(clearTimer)

  return visible
}
