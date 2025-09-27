import { Api } from './__generated'

const BASE_URL = ''

// 导出全局变量`api`
export const api = new Api(async ({ uri, method, headers, body }) => {
  const tenant = (window as any).__tenant as string | undefined
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

  // 401处理：排除获取token的接口，避免循环
  if (response.status === 401 && !(uri.includes('/api/token') && method.includes('GET'))) {
    // 清除 token
    document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'

    window.alert('登录已过期，请重新登录')
    window.location.replace('/')
  }

  if (Math.floor(response.status / 100) === 5) {
    console.error('服务器错误:', response.status, uri, await response.text())
    throw new Error('服务端内部错误，请检查服务器状态或稍后再试！')
  }

  if (Math.floor(response.status / 100) !== 2) {
    throw response.json()
  }

  const text = await response.text()
  if (text.length === 0) {
    return null
  }
  return JSON.parse(text)
})
