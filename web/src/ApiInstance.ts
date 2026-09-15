import { Api } from './__generated'
import { i18n } from '@/i18n'

const BASE_URL = ''

declare global {
  interface Window {
    __tenant?: string
  }
}

let unauthorizedHandled = false

// 导出全局变量`api`
export const api = new Api(async ({ uri, method, headers, body }) => {
  const tenant = window.__tenant
  const isFormData = body instanceof FormData
  const fetchHeaders: HeadersInit = {
    ...headers,
    ...(tenant !== undefined && tenant !== '' ? { tenant } : {}),
  }
  if (!isFormData) {
    // 仅在非FormData时设置content-type，携带二进制文件时，浏览器会自动设置content-type
    fetchHeaders['content-type'] = 'application/json;charset=UTF-8'
  }
  const response = await fetch(`${BASE_URL}${uri}`, {
    method,
    body: isFormData ? body : JSON.stringify(body),
    headers: fetchHeaders,
  })

  // 401处理：排除获取token的接口，避免循环；并发的多次 401 只提示并跳转一次
  if (response.status === 401 && !(uri.includes('/api/token') && method.includes('GET'))) {
    if (!unauthorizedHandled) {
      unauthorizedHandled = true
      document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
      window.alert(i18n.global.t('misc.session.expiredRelogin'))
      window.location.replace('/')
    }
    throw new Error(i18n.global.t('misc.session.expired'))
  }

  if (Math.floor(response.status / 100) === 5) {
    const text = await response.text()
    console.error('服务器错误:', response.status, uri, text)
    throw new Error(i18n.global.t('misc.request.failedWithDetail', { detail: text }))
  }

  if (Math.floor(response.status / 100) !== 2) {
    const errBody = (await response.json().catch(() => null)) as {
      description?: string
      message?: string
    } | null
    throw new Error(
      errBody?.description ||
        errBody?.message ||
        i18n.global.t('misc.request.failedWithStatus', { status: response.status }),
    )
  }

  const text = await response.text()
  if (text.length === 0) {
    return null
  }
  return JSON.parse(text)
})
