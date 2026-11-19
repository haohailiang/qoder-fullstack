/**
 * 保险咨询机器人 i18n 文案校验
 *
 * 覆盖：
 * - zh / en 语言包键集合完全一致（避免漏翻译）
 * - 保险咨询机器人所需文案键齐全
 */
import { describe, it, expect } from 'vitest'
import zh from '../../../i18n/zh'
import en from '../../../i18n/en'

type Dict = Record<string, unknown>

/** 把嵌套语言包拍平成 a.b.c -> string */
function flatten(source: Dict, prefix = ''): Record<string, string> {
  const result: Record<string, string> = {}
  Object.entries(source).forEach(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object') {
      Object.assign(result, flatten(value as Dict, path))
    } else {
      result[path] = String(value)
    }
  })
  return result
}

const REQUIRED_INSURANCE_KEYS = [
  'insurance.drawerTitle',
  'insurance.tabChat',
  'insurance.tabKnowledge',
  'insurance.tabCheckpoints',
  'insurance.newSession',
  'insurance.inputPlaceholder',
  'insurance.send',
  'insurance.stop',
  'insurance.sources',
  'insurance.upload',
  'insurance.statusReady',
  'insurance.statusProcessing',
  'insurance.statusFailed',
  'insurance.restore',
  'insurance.noCheckpoints',
]

describe('i18n 保险文案', () => {
  const zhFlat = flatten(zh as Dict)
  const enFlat = flatten(en as Dict)

  it('zh 与 en 的键集合应一致', () => {
    expect(Object.keys(zhFlat).sort()).toEqual(Object.keys(enFlat).sort())
  })

  it('应包含保险机器人所需文案', () => {
    REQUIRED_INSURANCE_KEYS.forEach((key) => {
      expect(zhFlat[key], `zh 缺少 ${key}`).toBeTruthy()
      expect(enFlat[key], `en 缺少 ${key}`).toBeTruthy()
    })
  })

  it('应提供保险分类与入口文案', () => {
    expect(zhFlat['product.categoryInsurance']).toBeTruthy()
    expect(zhFlat['product.openBot']).toBeTruthy()
    expect(enFlat['product.categoryInsurance']).toBeTruthy()
    expect(enFlat['product.openBot']).toBeTruthy()
  })
})