import { getDocEntry, getDocHref, getDocsRootHref, getOtherDocsLocale, type DocsLocale } from '../features/docs/registry'
import { defaultDocsLocale, getLegacyRedirect, normalizePathname } from './routing'

const SITE_URL = import.meta.env.VITE_SITE_URL ?? 'http://localhost:5173'

interface AlternateLink {
  href: string
  hrefLang: string
}

export interface PageSeoData {
  alternates: AlternateLink[]
  canonical: string
  description: string
  lang: string
  robots?: string
  title: string
}

function toAbsoluteUrl(pathname: string) {
  return new URL(pathname, SITE_URL).toString()
}

function normalizeSlug(slug: string) {
  return slug.replace(/^\/+|\/+$/g, '')
}

function buildDocsAlternates(locale: DocsLocale, slug: string) {
  const currentPath = getDocHref(locale, slug)
  const otherLocale = getOtherDocsLocale(locale)
  const defaultHref = getDocEntry(defaultDocsLocale, slug)
    ? getDocHref(defaultDocsLocale, slug)
    : currentPath
  const links: AlternateLink[] = [
    {
      href: toAbsoluteUrl(currentPath),
      hrefLang: locale,
    },
  ]

  if (getDocEntry(otherLocale, slug)) {
    links.push({
      href: toAbsoluteUrl(getDocHref(otherLocale, slug)),
      hrefLang: otherLocale,
    })
  }

  links.push({
    href: toAbsoluteUrl(defaultHref),
    hrefLang: 'x-default',
  })

  return links
}

export function getHomeSeo(locale: DocsLocale = defaultDocsLocale): PageSeoData {
  return {
    alternates: [],
    canonical: toAbsoluteUrl('/'),
    description:
      locale === 'zh-CN'
        ? 'Xiaomi Album Syncer - 全量 / 增量 / 定时下载小米云服务中的相册与录音到本地'
        : 'Xiaomi Album Syncer - Full / incremental / scheduled download of albums and recordings from Xiaomi Cloud to local storage',
    lang: locale,
    title: 'Xiaomi Album Syncer',
  }
}

export function getDocsSeo(pathname: string, locale: DocsLocale, slug: string): PageSeoData {
  const normalizedSlug = normalizeSlug(slug)
  const entry = getDocEntry(locale, normalizedSlug)

  if (entry === null) {
    return getNotFoundSeo('/404')
  }

  const canonicalPath = pathname === '/docs' ? getDocsRootHref(defaultDocsLocale) : getDocHref(locale, entry.meta.slug)

  return {
    alternates: buildDocsAlternates(locale, entry.meta.slug),
    canonical: toAbsoluteUrl(canonicalPath),
    description: entry.meta.description,
    lang: locale,
    title: `${entry.meta.title} | Xiaomi Album Syncer Docs`,
  }
}

export function getRedirectSeo(targetPath: string, locale: DocsLocale): PageSeoData {
  return {
    alternates: [],
    canonical: toAbsoluteUrl(targetPath),
    description:
      locale === 'zh-CN'
        ? '文档页面已迁移，正在跳转到最新地址。'
        : 'The documentation page has moved and is redirecting to the latest location.',
    lang: locale,
    robots: 'noindex',
    title: 'Redirecting | Xiaomi Album Syncer Docs',
  }
}

export function getNotFoundSeo(pathname = '/404'): PageSeoData {
  return {
    alternates: [],
    canonical: toAbsoluteUrl(pathname),
    description: 'The requested page does not exist on the Xiaomi Album Syncer website.',
    lang: defaultDocsLocale,
    robots: 'noindex',
    title: '404 | Xiaomi Album Syncer',
  }
}

export function getSeoForPath(pathname: string): PageSeoData {
  const normalizedPathname = normalizePathname(pathname)

  if (normalizedPathname === '/') {
    return getHomeSeo(defaultDocsLocale)
  }

  const redirect = getLegacyRedirect(normalizedPathname)

  if (redirect) {
    return getRedirectSeo(redirect.to, redirect.locale)
  }

  if (normalizedPathname === '/docs') {
    return getRedirectSeo(getDocsRootHref(defaultDocsLocale), defaultDocsLocale)
  }

  const docsMatch = normalizedPathname.match(/^\/(zh-CN|en-US)\/docs(?:\/(.*))?$/)

  if (docsMatch) {
    const locale = docsMatch[1] as DocsLocale
    const slug = docsMatch[2] ?? ''

    if (getDocEntry(locale, slug) === null) {
      return getNotFoundSeo('/404')
    }

    return getDocsSeo(normalizedPathname, locale, slug)
  }

  return getNotFoundSeo(normalizedPathname === '/404' ? '/404' : normalizedPathname)
}

function upsertMeta(name: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[data-xas-seo="${name}"]`)

  if (element === null && name === 'description') {
    element = document.head.querySelector<HTMLMetaElement>('meta[name="description"]')
    element?.setAttribute('data-xas-seo', name)
  }

  if (element === null && name === 'robots') {
    element = document.head.querySelector<HTMLMetaElement>('meta[name="robots"]')
    element?.setAttribute('data-xas-seo', name)
  }

  if (element === null) {
    element = document.createElement('meta')
    element.setAttribute('data-xas-seo', name)
    element.setAttribute('name', name)
    document.head.append(element)
  }

  element.setAttribute('content', content)
}

function upsertCanonical(href: string) {
  let element = document.head.querySelector<HTMLLinkElement>('link[data-xas-seo="canonical"]')

  if (element === null) {
    element = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    element?.setAttribute('data-xas-seo', 'canonical')
  }

  if (element === null) {
    element = document.createElement('link')
    element.setAttribute('data-xas-seo', 'canonical')
    element.setAttribute('rel', 'canonical')
    document.head.append(element)
  }

  element.setAttribute('href', href)
}

export function applySeoToDocument(seo: PageSeoData) {
  document.title = seo.title
  document.documentElement.lang = seo.lang
  upsertMeta('description', seo.description)
  upsertCanonical(seo.canonical)

  const robots = document.head.querySelector<HTMLMetaElement>('meta[data-xas-seo="robots"]')

  if (seo.robots) {
    if (robots === null) {
      const element = document.createElement('meta')
      element.setAttribute('data-xas-seo', 'robots')
      element.setAttribute('name', 'robots')
      element.setAttribute('content', seo.robots)
      document.head.append(element)
    } else {
      robots.setAttribute('content', seo.robots)
    }
  } else if (robots !== null) {
    robots.remove()
  }

  for (const element of document.head.querySelectorAll('link[data-xas-seo="alternate"]')) {
    element.remove()
  }

  for (const alternate of seo.alternates) {
    const element = document.createElement('link')
    element.setAttribute('data-xas-seo', 'alternate')
    element.setAttribute('rel', 'alternate')
    element.setAttribute('hreflang', alternate.hrefLang)
    element.setAttribute('href', alternate.href)
    document.head.append(element)
  }
}
