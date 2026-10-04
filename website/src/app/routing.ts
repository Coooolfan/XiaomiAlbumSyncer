import { getDocHref, getDocsRootHref, listLocaleDocs, type DocsLocale } from '../features/docs/registry'

export const defaultDocsLocale: DocsLocale = 'zh-CN'

export interface DocsRedirect {
  from: string
  to: string
  locale: DocsLocale
}

export const docsLegacyRedirects: readonly DocsRedirect[] = []

export function normalizePathname(pathname: string) {
  if (pathname === '/') {
    return pathname
  }

  return pathname.replace(/\/+$/, '') || '/'
}

export function getLegacyRedirect(pathname: string) {
  const normalizedPathname = normalizePathname(pathname)
  return docsLegacyRedirects.find((entry) => entry.from === normalizedPathname) ?? null
}

export function listPrerenderPaths() {
  const paths = new Set<string>(['/', '/docs', '/404'])

  for (const locale of ['zh-CN', 'en-US'] as const) {
    for (const entry of listLocaleDocs(locale)) {
      paths.add(entry.meta.slug ? getDocHref(locale, entry.meta.slug) : getDocsRootHref(locale))
    }
  }

  for (const redirect of docsLegacyRedirects) {
    paths.add(redirect.from)
  }

  return [...paths]
}
