/**
 * 前端用户行为监控埋点工具
 *
 * 所有上报均为异步非阻塞，异常静默捕获，不影响业务逻辑。
 */

// ---------- 类型定义 ----------

/** 埋点事件的公共上下文 */
interface TrackContext {
  timestamp: number
  userId: string | null
  pageName: string
  platform: string
}

/** 埋点事件完整载荷 */
interface TrackPayload {
  eventName: string
  properties: Record<string, unknown>
  context: TrackContext
}

// ---------- 上下文采集 ----------

function buildContext(pageName: string): TrackContext {
  let userId: string | null = null
  try {
    // 优先从 localStorage 获取（base-app 登录后写入）
    userId = localStorage.getItem('demo_token')
    // 若只需纯 ID 可在此处解码 JWT，当前仅做透传标识
  } catch {
    // 跨域 / 隐私模式下 localStorage 可能不可用，静默忽略
  }

  return {
    timestamp: Date.now(),
    userId,
    pageName,
    platform: 'product-app',
  }
}

// ---------- 上报通道 ----------

/**
 * 统一埋点上报函数
 *
 * 当前实现为 console.debug + 模拟异步发送；
 * 生产环境可替换为真正的 Beacon / Fetch 上报。
 */
let _sendQueue: TrackPayload[] = []
let _flushTimer: ReturnType<typeof setTimeout> | null = null

function enqueue(payload: TrackPayload) {
  _sendQueue.push(payload)

  // 微任务批处理：100ms 内的上报合并一次发送
  if (_flushTimer === null) {
    _flushTimer = setTimeout(() => {
      const batch = _sendQueue.splice(0)
      _flushTimer = null
      try {
        // ---- 实际项目中替换为真实上报地址 ----
        if (import.meta.env.DEV) {
          console.debug('[tracker] batch send', batch)
        }
        // navigator.sendBeacon('/api/track', JSON.stringify(batch))
      } catch {
        // 上报失败静默忽略
      }
    }, 100)
  }
}

// ---------- 对外 API ----------

/**
 * 触发一条埋点事件
 *
 * @param eventName 事件名称，如 'PageView' | 'ItemExpose' | 'Click'
 * @param properties 事件携带的业务属性
 * @param pageName 当前页面标识
 */
export function trackEvent(
  eventName: string,
  properties: Record<string, unknown>,
  pageName = 'ProductList',
) {
  try {
    const payload: TrackPayload = {
      eventName,
      properties,
      context: buildContext(pageName),
    }
    enqueue(payload)
  } catch {
    // 任何异常静默捕获，绝不影响业务
  }
}

// ---------- 便捷方法 ----------

/** 页面访问埋点 */
export function trackPageView(pageName: string, extra?: Record<string, unknown>) {
  trackEvent('PageView', { ...extra }, pageName)
}

/** 商品曝光埋点 */
export function trackItemExpose(
  productId: number,
  productName: string,
  isSeckill: boolean,
  extra?: Record<string, unknown>,
) {
  trackEvent(
    'ItemExpose',
    { productId, productName, isSeckill, ...extra },
    'ProductList',
  )
}

/** 交互点击埋点 */
export function trackClick(
  productId: number,
  actionType: string,
  extra?: Record<string, unknown>,
) {
  trackEvent(
    'Click',
    { productId, actionType, ...extra },
    'ProductList',
  )
}
