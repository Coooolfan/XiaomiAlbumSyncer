import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

import { defineConfig } from 'vite'
import type { Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import mdx from '@mdx-js/rollup'
import tailwindcss from '@tailwindcss/vite'
import remarkGfm from 'remark-gfm'
import rehypeSlug from 'rehype-slug'
import rehypeShiki from '@shikijs/rehype'

const mdxPlugin = mdx({
  remarkPlugins: [remarkGfm],
  rehypePlugins: [
    rehypeSlug,
    [
      rehypeShiki,
      {
        themes: {
          light: 'github-light',
          dark: 'github-dark',
        },
        defaultColor: false,
        defaultLanguage: 'text',
        fallbackLanguage: 'text',
      },
    ],
  ],
}) as Plugin

mdxPlugin.enforce = 'pre'

const SITE_URL = process.env.VITE_SITE_URL ?? 'http://localhost:5173'
const DOCS_DIR = path.resolve(import.meta.dirname, 'src/docs')
const LOCALES = ['zh-CN', 'en-US'] as const

interface SitemapEntry {
  // canonical locale for this entry (used as x-default)
  defaultLocale: (typeof LOCALES)[number]
  // map of locale -> absolute URL
  alternates: Partial<Record<(typeof LOCALES)[number], string>>
}

function previewDirectoryRedirectPlugin(): Plugin {
  let outDir = path.resolve(import.meta.dirname, 'dist')

  return {
    name: 'preview-directory-redirect',
    configResolved(config) {
      outDir = path.resolve(config.root, config.build.outDir)
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url || (req.method !== 'GET' && req.method !== 'HEAD')) {
          next()
          return
        }

        const requestUrl = new URL(req.url, 'http://localhost')
        const pathname = requestUrl.pathname

        if (pathname === '/' || pathname.endsWith('/') || path.extname(pathname)) {
          next()
          return
        }

        const targetIndex = path.join(outDir, pathname.replace(/^\//, ''), 'index.html')

        if (!fs.existsSync(targetIndex)) {
          next()
          return
        }

        res.statusCode = 308
        res.setHeader('Location', `${pathname}/${requestUrl.search}`)
        res.end()
      })
    },
  }
}

function sitemapPlugin(): Plugin {
  return {
    name: 'sitemap',
    generateBundle() {
      const entries: SitemapEntry[] = []

      // Homepage — locale-neutral, x-default points to itself
      entries.push({
        defaultLocale: 'zh-CN',
        alternates: { 'zh-CN': `${SITE_URL}/` },
      })

      const docsLandingEntry = [
        '  <url>',
        `    <loc>${SITE_URL}/docs/</loc>`,
        `    <xhtml:link rel="alternate" hreflang="x-default" href="${SITE_URL}/docs/"/>`,
        '  </url>',
      ].join('\n')

      // Collect slugs per locale
      const slugsByLocale: Partial<Record<(typeof LOCALES)[number], string[]>> = {}
      for (const locale of LOCALES) {
        const localeDir = path.join(DOCS_DIR, locale)
        const slugs: string[] = [''] // '' = docs root
        const files = fs.readdirSync(localeDir).filter((f) => f.endsWith('.mdx'))
        for (const file of files) {
          const content = fs.readFileSync(path.join(localeDir, file), 'utf-8')
          const match = content.match(/slug:\s*['"]([^'"]*)['"]/m)
          if (match === null) continue
          const slug = match[1].replace(/^\/+|\/+$/g, '')
          if (slug) slugs.push(slug)
        }
        slugsByLocale[locale] = slugs
      }

      // Build entries: pair up slugs that exist in both locales
      const zhSlugs = new Set(slugsByLocale['zh-CN'] ?? [])
      const enSlugs = new Set(slugsByLocale['en-US'] ?? [])
      const allSlugs = new Set([...zhSlugs, ...enSlugs])

      const docUrl = (locale: (typeof LOCALES)[number], slug: string) =>
        slug ? `${SITE_URL}/${locale}/docs/${slug}/` : `${SITE_URL}/${locale}/docs/`

      for (const slug of allSlugs) {
        const alternates: SitemapEntry['alternates'] = {}
        if (zhSlugs.has(slug)) alternates['zh-CN'] = docUrl('zh-CN', slug)
        if (enSlugs.has(slug)) alternates['en-US'] = docUrl('en-US', slug)
        entries.push({ defaultLocale: 'zh-CN', alternates })
      }

      // Render XML — each locale URL gets its own <url> entry, all sharing the same alternates
      const renderEntry = (entry: SitemapEntry) => {
        const xDefault = entry.alternates[entry.defaultLocale] ?? Object.values(entry.alternates)[0]!
        const altLinks = (Object.entries(entry.alternates) as [(typeof LOCALES)[number], string][]).map(
          ([locale, url]) => `    <xhtml:link rel="alternate" hreflang="${locale}" href="${url}"/>`,
        )
        altLinks.push(`    <xhtml:link rel="alternate" hreflang="x-default" href="${xDefault}"/>`)

        return Object.values(entry.alternates)
          .map((loc) => ['  <url>', `    <loc>${loc}</loc>`, ...altLinks, '  </url>'].join('\n'))
          .join('\n')
      }

      const sitemap = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"',
        '        xmlns:xhtml="http://www.w3.org/1999/xhtml">',
        docsLandingEntry,
        ...entries.map(renderEntry),
        '</urlset>',
      ].join('\n')

      this.emitFile({ type: 'asset', fileName: 'sitemap.xml', source: sitemap })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  build: {
    manifest: true,
  },
  plugins: [
    mdxPlugin,
    previewDirectoryRedirectPlugin(),
    sitemapPlugin(),
    react({
      include: /\.(mdx|js|jsx|ts|tsx)$/,
    }),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
