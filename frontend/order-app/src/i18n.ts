/**
 * 轻量级国际化模块（order-app 使用原生 TS，无需完整 i18n 库）
 */

const LANG_KEY = 'qoder_lang'

type Lang = 'zh' | 'en'

const translations: Record<Lang, Record<string, string>> = {
  zh: {
    // 页面标题与按钮
    'order.title': '订单管理',
    'order.myOrders': '我的订单',
    'order.allOrders': '全部订单',
    'order.loading': '加载中...',
    'order.empty': '暂无订单数据',
    // 表格列标题
    'order.colOrderNo': '订单号',
    'order.colProduct': '商品',
    'order.colQuantity': '数量',
    'order.colAmount': '金额',
    'order.colStatus': '状态',
    'order.colActions': '操作',
    // 状态
    'order.statusPending': '待支付',
    'order.statusPaid': '已支付',
    'order.statusShipped': '已发货',
    'order.statusCompleted': '已完成',
    'order.statusCancelled': '已取消',
    'order.statusUnknown': '未知',
    // 操作
    'order.detail': '详情',
    'order.cancel': '取消',
    'order.backToList': '← 返回列表',
    'order.orderDetail': '订单详情',
    // 详情字段
    'order.detailOrderNo': '订单号:',
    'order.detailProduct': '商品名称:',
    'order.detailQuantity': '购买数量:',
    'order.detailAmount': '订单金额:',
    'order.detailStatus': '订单状态:',
    'order.detailAddress': '收货地址:',
    'order.detailRemark': '备注:',
    'order.detailCreated': '创建时间:',
    // 消息
    'order.fetchFailed': '获取订单列表失败',
    'order.confirmCancel': '确定要取消此订单吗？',
    'order.cancelSuccess': '订单已取消',
    'order.cancelFailed': '取消订单失败',
    // SMS 记录
    'sms.title': '短信发送记录',
    'sms.empty': '暂无发送记录',
    'sms.colPhone': '手机号',
    'sms.colCode': '短信验证码',
    'sms.colStatus': '发送状态',
    'sms.colTime': '发送时间',
    'sms.statusSuccess': '成功',
    'sms.statusFailed': '失败',
  },
  en: {
    // Page title & buttons
    'order.title': 'Orders',
    'order.myOrders': 'My Orders',
    'order.allOrders': 'All Orders',
    'order.loading': 'Loading...',
    'order.empty': 'No order data',
    // Table columns
    'order.colOrderNo': 'Order No.',
    'order.colProduct': 'Product',
    'order.colQuantity': 'Qty',
    'order.colAmount': 'Amount',
    'order.colStatus': 'Status',
    'order.colActions': 'Actions',
    // Status
    'order.statusPending': 'Pending',
    'order.statusPaid': 'Paid',
    'order.statusShipped': 'Shipped',
    'order.statusCompleted': 'Completed',
    'order.statusCancelled': 'Cancelled',
    'order.statusUnknown': 'Unknown',
    // Actions
    'order.detail': 'Detail',
    'order.cancel': 'Cancel',
    'order.backToList': '← Back to List',
    'order.orderDetail': 'Order Detail',
    // Detail fields
    'order.detailOrderNo': 'Order No:',
    'order.detailProduct': 'Product:',
    'order.detailQuantity': 'Quantity:',
    'order.detailAmount': 'Amount:',
    'order.detailStatus': 'Status:',
    'order.detailAddress': 'Address:',
    'order.detailRemark': 'Remark:',
    'order.detailCreated': 'Created:',
    // Messages
    'order.fetchFailed': 'Failed to fetch orders',
    'order.confirmCancel': 'Are you sure to cancel this order?',
    'order.cancelSuccess': 'Order cancelled',
    'order.cancelFailed': 'Failed to cancel order',
    // SMS records
    'sms.title': 'SMS Records',
    'sms.empty': 'No SMS records',
    'sms.colPhone': 'Phone',
    'sms.colCode': 'Code',
    'sms.colStatus': 'Status',
    'sms.colTime': 'Time',
    'sms.statusSuccess': 'Success',
    'sms.statusFailed': 'Failed',
  },
}

/** 获取当前语言 */
function getLang(): Lang {
  const stored = localStorage.getItem(LANG_KEY)
  if (stored === 'zh' || stored === 'en') return stored
  return 'zh'
}

/** 翻译函数 */
export function t(key: string): string {
  const lang = getLang()
  return translations[lang][key] || translations.zh[key] || key
}

/** 获取当前语言 */
export function getCurrentLang(): Lang {
  return getLang()
}
