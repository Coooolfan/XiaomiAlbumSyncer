import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export type AppLocale = 'zh-CN' | 'en-US'
export const SUPPORTED_LOCALES: ReadonlyArray<AppLocale> = ['zh-CN', 'en-US']

export function detectLocale(): AppLocale {
  try {
    const lang = typeof navigator !== 'undefined' ? navigator.language : ''
    return lang.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
  } catch {
    return 'zh-CN'
  }
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

type MessageSchema = typeof zhCN

declare module 'vue-i18n' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface DefineLocaleMessage extends MessageSchema {}
}

export const primevueLocales: Record<AppLocale, Record<string, unknown>> = {
  'zh-CN': {
    emptyMessage: '暂无数据',
    emptyFilterMessage: '无匹配结果',
    emptySearchMessage: '未找到结果',
    passwordPrompt: '请输入密码',
    weak: '弱',
    medium: '中',
    strong: '强',
  },
  'en-US': {
    emptyMessage: 'No records found',
    emptyFilterMessage: 'No results found',
    emptySearchMessage: 'No results found',
    passwordPrompt: 'Enter a password',
    weak: 'Weak',
    medium: 'Medium',
    strong: 'Strong',
  },
}
