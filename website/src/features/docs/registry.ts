import type { ComponentType } from 'react'

export const docsLocales = ['zh-CN', 'en-US'] as const

export type DocsLocale = (typeof docsLocales)[number]

export interface DocMeta {
  title: string
  description: string
  section: string
  order: number
  slug: string
}

interface DocModule {
  default: ComponentType<Record<string, unknown>>
  meta: DocMeta
}

export interface DocEntry {
  locale: DocsLocale
  meta: DocMeta
  Component: ComponentType<Record<string, unknown>>
}

export interface DocsSection {
  title: string
  docs: DocEntry[]
}

export const docsLocaleLabels: Record<DocsLocale, string> = {
  'zh-CN': '简体中文',
  'en-US': 'English',
}

const modules = import.meta.glob<DocModule>('../../docs/{zh-CN,en-US}/**/*.mdx', {
  eager: true,
})

function normalizeSlug(slug: string) {
  return slug.replace(/^\/+|\/+$/g, '')
}

function withTrailingSlash(pathname: string) {
  return pathname.endsWith('/') ? pathname : `${pathname}/`
}

function assertDocsLocale(value: string): DocsLocale {
  if (isDocsLocale(value)) {
    return value
  }

  throw new Error(`Unsupported docs locale: ${value}`)
}

function createEntries() {
  const entries: DocEntry[] = []
  const seen = new Set<string>()

  for (const [filePath, module] of Object.entries(modules)) {
    const localeMatch = filePath.match(/\/(zh-CN|en-US)\//)

    if (!localeMatch) {
      throw new Error(`Unable to determine docs locale from ${filePath}`)
    }

    const locale = assertDocsLocale(localeMatch[1])
    const slug = normalizeSlug(module.meta.slug)
    const dedupeKey = `${locale}:${slug}`

    if (seen.has(dedupeKey)) {
      throw new Error(`Duplicate docs slug detected: ${dedupeKey}`)
    }

    seen.add(dedupeKey)
    entries.push({
      locale,
      Component: module.default,
      meta: {
        ...module.meta,
        slug,
      },
    })
  }

  return entries.sort((left, right) => {
    if (left.locale !== right.locale) {
      return left.locale.localeCompare(right.locale)
    }

    if (left.meta.order !== right.meta.order) {
      return left.meta.order - right.meta.order
    }

    return left.meta.title.localeCompare(right.meta.title)
  })
}

const docEntries = createEntries()

export function isDocsLocale(value: string): value is DocsLocale {
  return docsLocales.includes(value as DocsLocale)
}

export function getPreferredDocsLocale(language: string): DocsLocale {
  return language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

export function getOtherDocsLocale(locale: DocsLocale): DocsLocale {
  return locale === 'zh-CN' ? 'en-US' : 'zh-CN'
}

export function getDocsRootHref(locale: DocsLocale) {
  return withTrailingSlash(`/${locale}/docs`)
}

export function getDocHref(locale: DocsLocale, slug: string) {
  const normalizedSlug = normalizeSlug(slug)
  return normalizedSlug ? withTrailingSlash(`/${locale}/docs/${normalizedSlug}`) : getDocsRootHref(locale)
}

export function listLocaleDocs(locale: DocsLocale) {
  return docEntries.filter((entry) => entry.locale === locale)
}

export function listLocaleSections(locale: DocsLocale): DocsSection[] {
  const grouped = new Map<string, DocEntry[]>()

  for (const entry of listLocaleDocs(locale)) {
    const sectionDocs = grouped.get(entry.meta.section) ?? []
    sectionDocs.push(entry)
    grouped.set(entry.meta.section, sectionDocs)
  }

  return [...grouped.entries()]
    .map(([title, docs]) => ({
      title,
      docs: docs.sort((left, right) => left.meta.order - right.meta.order),
    }))
    .sort((left, right) => left.docs[0].meta.order - right.docs[0].meta.order)
}

export function getDocEntry(locale: DocsLocale, slug: string) {
  const normalizedSlug = normalizeSlug(slug)
  return listLocaleDocs(locale).find((entry) => entry.meta.slug === normalizedSlug) ?? null
}

export function getAlternateDocHref(locale: DocsLocale, slug: string) {
  const otherLocale = getOtherDocsLocale(locale)
  return getDocEntry(otherLocale, slug)
    ? getDocHref(otherLocale, slug)
    : getDocsRootHref(otherLocale)
}
