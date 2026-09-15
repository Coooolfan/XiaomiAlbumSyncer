import { createJiti } from 'jiti'
import { createI18n } from 'vue-i18n'

const jiti = createJiti(import.meta.url)
const [{ default: zhCN }, { default: enUS }] = await Promise.all([
  jiti.import('../src/i18n/locales/zh-CN.ts'),
  jiti.import('../src/i18n/locales/en-US.ts'),
])

const locales = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

function collectMessageKeys(messages, path = [], keys = []) {
  for (const [key, value] of Object.entries(messages)) {
    const nextPath = [...path, key]
    if (typeof value === 'string') {
      keys.push(nextPath.join('.'))
    } else if (value && typeof value === 'object' && !Array.isArray(value)) {
      collectMessageKeys(value, nextPath, keys)
    } else {
      throw new TypeError(`Unsupported locale value at ${nextPath.join('.')}`)
    }
  }
  return keys
}

const failures = []
const localeKeys = Object.fromEntries(
  Object.entries(locales).map(([locale, messages]) => [
    locale,
    collectMessageKeys(messages).sort(),
  ]),
)
const referenceLocale = Object.keys(locales)[0]
const referenceKeys = new Set(localeKeys[referenceLocale])

for (const [locale, keys] of Object.entries(localeKeys)) {
  const keySet = new Set(keys)
  for (const key of referenceKeys) {
    if (!keySet.has(key)) failures.push(`${locale}: missing key ${key}`)
  }
  for (const key of keySet) {
    if (!referenceKeys.has(key)) failures.push(`${locale}: unexpected key ${key}`)
  }
}

for (const [locale, messages] of Object.entries(locales)) {
  const i18n = createI18n({
    legacy: false,
    locale,
    fallbackLocale: false,
    missingWarn: false,
    fallbackWarn: false,
    messages: { [locale]: messages },
  })
  for (const key of localeKeys[locale]) {
    try {
      i18n.global.t(key)
    } catch (error) {
      failures.push(`${locale}:${key}: ${error instanceof Error ? error.message : String(error)}`)
    }
  }
}

if (failures.length > 0) {
  console.error(`i18n check failed:\n${failures.map((failure) => `- ${failure}`).join('\n')}`)
  process.exitCode = 1
} else {
  const messageCount = Object.values(localeKeys).reduce((sum, keys) => sum + keys.length, 0)
  console.log(
    `i18n check passed (${Object.keys(locales).length} locales, ${messageCount} messages)`,
  )
}
