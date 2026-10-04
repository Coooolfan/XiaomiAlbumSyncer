import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Languages, List, Moon, Sun, X } from 'lucide-react'
import {
  type DocsLocale,
  docsLocaleLabels,
  getAlternateDocHref,
  getDocEntry,
  getDocHref,
  getOtherDocsLocale,
  listLocaleSections,
} from './registry'
import { mdxComponents } from './mdx-components'
import { useTheme } from '@/hooks/useTheme'
import { setLocale } from '@/i18n'

const GITHUB_URL = 'https://github.com/Coooolfan/XiaomiAlbumSyncer'

function GithubIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
    </svg>
  )
}

interface TocItem {
  id: string
  title: string
  level: 2 | 3 | 4
}

interface TocHighlight {
  top: number
  height: number
  visible: boolean
}

function useTableOfContents(key: string) {
  const [items, setItems] = useState<TocItem[]>([])

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const container = document.getElementById('docs-content')

      if (!container) {
        setItems([])
        return
      }

      const headings = [...container.querySelectorAll<HTMLHeadingElement>('h2[id], h3[id], h4[id]')].map((heading) => {
        const level: TocItem['level'] = heading.tagName === 'H2' ? 2 : heading.tagName === 'H3' ? 3 : 4

        return {
          id: heading.id,
          title: heading.textContent?.trim() ?? '',
          level,
        }
      })

      setItems(headings.filter((heading) => heading.title))
    })

    return () => window.cancelAnimationFrame(frame)
  }, [key])

  return items
}

function areSameIds(left: string[], right: string[]) {
  if (left.length !== right.length) {
    return false
  }

  return left.every((value, index) => value === right[index])
}

function useVisibleTocItems(items: TocItem[], key: string) {
  const [activeIds, setActiveIds] = useState<string[]>([])

  useEffect(() => {
    if (!items.length) {
      setActiveIds((current) => (current.length ? [] : current))
      return
    }

    let frame = 0

    const updateVisibleSections = () => {
      const container = document.getElementById('docs-content')

      if (!container) {
        setActiveIds((current) => (current.length ? [] : current))
        return
      }

      const headings = items
        .map((item) => document.getElementById(item.id))
        .filter((heading): heading is HTMLElement => heading instanceof HTMLElement)

      if (!headings.length) {
        setActiveIds((current) => (current.length ? [] : current))
        return
      }

      const viewportTop = window.scrollY + 104
      const viewportBottom = window.scrollY + window.innerHeight - 32
      const contentBottom = container.getBoundingClientRect().bottom + window.scrollY
      const nextActiveIds = headings.flatMap((heading, index) => {
        const sectionTop = heading.getBoundingClientRect().top + window.scrollY
        const nextHeading = headings[index + 1]
        const sectionBottom = nextHeading
          ? nextHeading.getBoundingClientRect().top + window.scrollY
          : contentBottom

        return sectionBottom > viewportTop && sectionTop < viewportBottom ? [heading.id] : []
      })

      setActiveIds((current) => (areSameIds(current, nextActiveIds) ? current : nextActiveIds))
    }

    const scheduleUpdate = () => {
      window.cancelAnimationFrame(frame)
      frame = window.requestAnimationFrame(updateVisibleSections)
    }

    scheduleUpdate()
    window.addEventListener('scroll', scheduleUpdate, { passive: true })
    window.addEventListener('resize', scheduleUpdate)

    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('scroll', scheduleUpdate)
      window.removeEventListener('resize', scheduleUpdate)
    }
  }, [items, key])

  return activeIds
}

function DocsNavigation({
  locale,
  currentSlug,
  onNavigate,
}: {
  locale: DocsLocale
  currentSlug: string
  onNavigate?: () => void
}) {
  const { t } = useTranslation()
  const sections = listLocaleSections(locale)

  return (
    <nav aria-label={t('docs.title')} className="space-y-6">
      {sections.map((section) => (
        <div key={section.title}>
          <p className="mb-2 text-xs font-semibold tracking-[0.18em] text-neutral-400 uppercase transition-colors duration-300 dark:text-neutral-500">
            {section.title}
          </p>
          <ul className="space-y-0.5">
            {section.docs.map((doc) => {
              const active = doc.meta.slug === currentSlug
              return (
                <li key={doc.meta.slug || '__root__'}>
                  <Link
                    to={getDocHref(locale, doc.meta.slug)}
                    onClick={onNavigate}
                    className={`block rounded-sm px-2.5 py-1.5 text-sm transition-colors duration-300 ${
                      active
                        ? 'bg-neutral-100 text-neutral-950 dark:bg-neutral-800 dark:text-white'
                        : 'text-neutral-600 hover:text-neutral-950 dark:text-neutral-400 dark:hover:text-white'
                    }`}
                  >
                    {doc.meta.title}
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </nav>
  )
}

function DocsTableOfContents({
  items,
  activeIds,
}: {
  items: TocItem[]
  activeIds: string[]
}) {
  const { t } = useTranslation()
  const listRef = useRef<HTMLUListElement | null>(null)
  const itemRefs = useRef<Record<string, HTMLAnchorElement | null>>({})
  const [highlight, setHighlight] = useState<TocHighlight>({ top: 0, height: 0, visible: false })

  useEffect(() => {
    const updateHighlight = () => {
      const list = listRef.current

      if (!list || !activeIds.length) {
        setHighlight((current) => (current.visible ? { top: 0, height: 0, visible: false } : current))
        return
      }

      const activeLinks = activeIds
        .map((id) => itemRefs.current[id])
        .filter((link): link is HTMLAnchorElement => link instanceof HTMLAnchorElement)

      if (!activeLinks.length) {
        setHighlight((current) => (current.visible ? { top: 0, height: 0, visible: false } : current))
        return
      }

      const listRect = list.getBoundingClientRect()
      const firstRect = activeLinks[0].getBoundingClientRect()
      const lastRect = activeLinks[activeLinks.length - 1].getBoundingClientRect()
      const nextHighlight = {
        top: firstRect.top - listRect.top,
        height: lastRect.bottom - firstRect.top,
        visible: true,
      }

      setHighlight((current) =>
        current.top === nextHighlight.top &&
        current.height === nextHighlight.height &&
        current.visible === nextHighlight.visible
          ? current
          : nextHighlight,
      )
    }

    updateHighlight()
    window.addEventListener('resize', updateHighlight)

    return () => {
      window.removeEventListener('resize', updateHighlight)
    }
  }, [activeIds, items])

  if (!items.length) {
    return null
  }

  return (
    <div>
      <p className="mb-3 text-xs font-semibold tracking-[0.18em] text-neutral-400 uppercase transition-colors duration-300 dark:text-neutral-500">
        {t('docs.onThisPage')}
      </p>
      <div className="relative">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 rounded-md bg-neutral-100 transition-[transform,height,opacity] duration-300 ease-out dark:bg-neutral-900/80"
          style={{
            height: `${highlight.height}px`,
            opacity: highlight.visible ? 1 : 0,
            transform: `translateY(${highlight.top}px)`,
          }}
        />
        <ul ref={listRef} className="relative space-y-1.5">
          {items.map((item) => {
            const active = activeIds.includes(item.id)

            return (
              <li key={item.id}>
                <a
                  href={`#${item.id}`}
                  ref={(node) => {
                    itemRefs.current[item.id] = node
                  }}
                  data-active={active ? 'true' : 'false'}
                  className={`relative z-10 block rounded-md px-2.5 py-1.5 text-sm transition-colors duration-300 ${
                    active
                      ? 'text-neutral-950 dark:text-white'
                      : 'text-neutral-500 hover:text-neutral-950 dark:text-neutral-500 dark:hover:text-white'
                  } ${item.level === 3 ? 'pl-5' : ''} ${item.level === 4 ? 'pl-8' : ''}`}
                >
                  {item.title}
                </a>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}

export function DocsPage({ locale }: { locale: DocsLocale }) {
  const params = useParams()
  const { t, i18n } = useTranslation()
  const currentSlug = (params['*'] ?? '').replace(/^\/+|\/+$/g, '')
  const entry = getDocEntry(locale, currentSlug)
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const tocItems = useTableOfContents(`${locale}:${currentSlug}:${entry?.meta.title ?? '404'}`)
  const activeTocItems = useVisibleTocItems(tocItems, `${locale}:${currentSlug}:${entry?.meta.title ?? '404'}`)
  const targetLocale = getOtherDocsLocale(locale)
  const { toggle } = useTheme()

  useEffect(() => {
    if (i18n.language !== locale) {
      setLocale(locale)
    }
  }, [locale, i18n.language])

  useEffect(() => {
    setIsMenuOpen(false)
  }, [locale, currentSlug])

  useEffect(() => {
    if (!entry) return
    const { hash } = window.location
    if (!hash) return
    const frame = requestAnimationFrame(() => {
      const el = document.getElementById(decodeURIComponent(hash.slice(1)))
      el?.scrollIntoView()
    })
    return () => cancelAnimationFrame(frame)
  }, [entry])

  if (!entry) {
    return null
  }

  const alternateHref = getAlternateDocHref(locale, entry.meta.slug)
  const CurrentDoc = entry.Component

  const headerBtnClass =
    'inline-flex items-center gap-2 rounded px-3 py-2 text-sm font-medium text-neutral-500 transition-colors duration-300 hover:text-black dark:text-neutral-400 dark:hover:text-white'

  return (
    <div className="min-h-screen bg-white text-black transition-colors duration-300 selection:bg-neutral-200 dark:bg-black dark:text-white dark:selection:bg-neutral-800">
      <header className="sticky top-0 z-40 border-b border-neutral-100 bg-white/90 backdrop-blur-sm transition-colors duration-300 dark:border-neutral-900 dark:bg-black/90">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-4">
            <Link
              to="/"
              className="flex items-center gap-2 text-sm font-semibold tracking-tight text-black transition-colors duration-300 dark:text-white"
            >
              <img src="/logo.png" alt="Xiaomi Album Syncer" className="h-5 w-5" />
              <span className="font-mono">Xiaomi Album Syncer</span>
            </Link>
            <div className="hidden h-5 w-px bg-neutral-200 transition-colors duration-300 md:block dark:bg-neutral-800" />
            <span className="hidden text-sm text-neutral-500 transition-colors duration-300 md:block">
              {t('docs.title')}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggle}
              className="rounded p-2 text-neutral-500 transition-colors duration-300 hover:text-black dark:text-neutral-400 dark:hover:text-white"
              aria-label={t('nav.theme')}
            >
              <Sun className="hidden h-4 w-4 dark:block" />
              <Moon className="h-4 w-4 dark:hidden" />
            </button>
            <Link to={alternateHref} aria-label={docsLocaleLabels[targetLocale]} className={headerBtnClass}>
              <Languages className="h-4 w-4" />
              <span className="hidden sm:inline">{docsLocaleLabels[targetLocale]}</span>
              <span className="sm:hidden">{t('docs.languageSwitch')}</span>
            </Link>
            <a href={GITHUB_URL} target="_blank" rel="noreferrer" className={`hidden sm:inline-flex ${headerBtnClass}`}>
              <GithubIcon className="h-4 w-4" />
              GitHub
            </a>
            <button type="button" onClick={() => setIsMenuOpen(true)} className={`lg:hidden ${headerBtnClass}`}>
              <List className="h-4 w-4" />
              {t('docs.menu')}
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto flex max-w-7xl gap-8 px-4 py-8 sm:px-6 lg:px-8">
        <aside className="docs-scrollbar sticky top-24 hidden h-[calc(100vh-7rem)] w-56 shrink-0 overflow-y-auto pr-4 lg:block">
          <DocsNavigation locale={locale} currentSlug={entry?.meta.slug ?? currentSlug} />
        </aside>

        <main className="min-w-0 flex-1 border-l border-neutral-200 pl-8 transition-colors duration-300 dark:border-neutral-800">
          <div className="px-2 py-2 sm:px-4">
            <article id="docs-content">
              <CurrentDoc components={mdxComponents} />
            </article>
          </div>
        </main>

        <aside className="docs-scrollbar sticky top-24 hidden h-[calc(100vh-7rem)] w-52 shrink-0 overflow-y-auto border-l border-neutral-200 pl-6 transition-colors duration-300 xl:block dark:border-neutral-800">
          <DocsTableOfContents items={tocItems} activeIds={activeTocItems} />
        </aside>
      </div>

      {isMenuOpen ? (
        <div className="fixed inset-0 z-50 bg-neutral-950/50 backdrop-blur-sm lg:hidden">
          <div className="ml-auto h-full w-full max-w-sm bg-white p-6 shadow-2xl transition-colors duration-300 dark:bg-black">
            <div className="mb-6 flex items-center justify-between">
              <p className="text-sm font-semibold tracking-[0.18em] text-neutral-400 uppercase transition-colors duration-300 dark:text-neutral-500">
                {t('docs.menu')}
              </p>
              <button
                type="button"
                onClick={() => setIsMenuOpen(false)}
                className="rounded p-2 text-neutral-500 transition-colors duration-300 hover:text-black dark:text-neutral-400 dark:hover:text-white"
                aria-label="Close menu"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <DocsNavigation
              locale={locale}
              currentSlug={entry?.meta.slug ?? currentSlug}
              onNavigate={() => setIsMenuOpen(false)}
            />
          </div>
        </div>
      ) : null}
    </div>
  )
}
