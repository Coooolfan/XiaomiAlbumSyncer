import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const rootDir = path.resolve(import.meta.dirname, '..')
const distDir = path.join(rootDir, 'dist')
const manifestPath = path.join(distDir, '.vite', 'manifest.json')
const serverEntryPath = path.join(distDir, 'server', 'entry-server.js')

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
const serverEntry = await import(pathToFileURL(serverEntryPath).href)

const themeInitScript = `<script>
      try {
        var t = localStorage.getItem('theme')
        var dark = t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches
        if (dark) document.documentElement.classList.add('dark')
      } catch (e) {}
    </script>`

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function ensurePosixPath(value) {
  return value.split(path.sep).join('/')
}

function findClientEntry() {
  const entries = Object.entries(manifest).filter(([, chunk]) => chunk.isEntry)

  const preferred = entries.find(([key, chunk]) => key === 'index.html' || key === 'src/main.tsx' || chunk.src === 'src/main.tsx')

  if (preferred) {
    return preferred[1]
  }

  if (entries.length === 0) {
    throw new Error('Unable to locate client entry in manifest')
  }

  return entries[0][1]
}

function collectCssFiles(chunk, seen = new Set()) {
  const cssFiles = new Set(chunk.css ?? [])

  for (const imported of chunk.imports ?? []) {
    if (seen.has(imported)) {
      continue
    }

    seen.add(imported)
    const importedChunk = manifest[imported]

    if (!importedChunk) {
      continue
    }

    for (const cssFile of collectCssFiles(importedChunk, seen)) {
      cssFiles.add(cssFile)
    }
  }

  return cssFiles
}

function renderHead(seo, clientEntry) {
  const cssLinks = [...collectCssFiles(clientEntry)]
    .map((href) => `<link rel="stylesheet" href="/${escapeHtml(ensurePosixPath(href))}">`)
    .join('\n    ')

  const alternateLinks = seo.alternates
    .map((alternate) => `<link data-xas-seo="alternate" rel="alternate" hreflang="${escapeHtml(alternate.hrefLang)}" href="${escapeHtml(alternate.href)}">`)
    .join('\n    ')

  const robotsMeta = seo.robots ? `<meta data-xas-seo="robots" name="robots" content="${escapeHtml(seo.robots)}">` : ''

  return [
    '<meta charset="UTF-8">',
    '<link rel="icon" type="image/png" href="/logo.png">',
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">',
    `<title>${escapeHtml(seo.title)}</title>`,
    `<meta data-xas-seo="description" name="description" content="${escapeHtml(seo.description)}">`,
    `<link data-xas-seo="canonical" rel="canonical" href="${escapeHtml(seo.canonical)}">`,
    robotsMeta,
    alternateLinks,
    cssLinks,
    themeInitScript,
  ].filter(Boolean).join('\n    ')
}

function renderDocument({ appHtml, seo }, clientEntry) {
  return `<!doctype html>
<html lang="${escapeHtml(seo.lang)}">
  <head>
    ${renderHead(seo, clientEntry)}
  </head>
  <body>
    <div id="root">${appHtml}</div>
    <script type="module" crossorigin src="/${escapeHtml(ensurePosixPath(clientEntry.file))}"></script>
  </body>
</html>
`
}

function renderDocsIndexDocument(page) {
  const detectScript = `<script>
      (function () {
        var locale = ${JSON.stringify(page.defaultHref.match(/^\/([^/]+)\//)[1])}
        try {
          var stored = localStorage.getItem('locale')
          if (stored === 'zh-CN' || stored === 'en-US') locale = stored
          else if (!/^zh/i.test(navigator.language || '')) locale = 'en-US'
        } catch (e) {}
        window.location.replace('/' + locale + '/docs/')
      })()
    </script>`

  return `<!doctype html>
<html lang="${escapeHtml(page.seo.lang)}">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapeHtml(page.seo.title)}</title>
    <meta data-xas-seo="description" name="description" content="${escapeHtml(page.seo.description)}">
    <meta data-xas-seo="robots" name="robots" content="${escapeHtml(page.seo.robots ?? 'noindex')}">
    <link data-xas-seo="canonical" rel="canonical" href="${escapeHtml(page.seo.canonical)}">
    <meta http-equiv="refresh" content="0; url=${escapeHtml(page.defaultHref)}">
    ${detectScript}
  </head>
  <body>
    <p>Redirecting to <a href="${escapeHtml(page.defaultHref)}">${escapeHtml(page.defaultHref)}</a>...</p>
  </body>
</html>
`
}

function renderRedirectDocument(page) {
  return `<!doctype html>
<html lang="${escapeHtml(page.seo.lang)}">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapeHtml(page.seo.title)}</title>
    <meta data-xas-seo="description" name="description" content="${escapeHtml(page.seo.description)}">
    <meta data-xas-seo="robots" name="robots" content="${escapeHtml(page.seo.robots ?? 'noindex')}">
    <link data-xas-seo="canonical" rel="canonical" href="${escapeHtml(page.seo.canonical)}">
    <meta http-equiv="refresh" content="0; url=${escapeHtml(page.redirectTo)}">
    <script>window.location.replace(${JSON.stringify(page.redirectTo)})</script>
  </head>
  <body>
    <p>Redirecting to <a href="${escapeHtml(page.redirectTo)}">${escapeHtml(page.redirectTo)}</a>...</p>
  </body>
</html>
`
}

function resolveOutputFile(pathname) {
  if (pathname === '/') {
    return path.join(distDir, 'index.html')
  }

  if (pathname === '/404') {
    return path.join(distDir, '404.html')
  }

  return path.join(distDir, pathname.replace(/^\//, ''), 'index.html')
}

function writeHtmlFile(targetFile, html) {
  fs.mkdirSync(path.dirname(targetFile), { recursive: true })
  fs.writeFileSync(targetFile, html)
}

const clientEntry = findClientEntry()
const pages = serverEntry.getPrerenderPages()

for (const page of pages) {
  const outputFile = resolveOutputFile(page.pathname)

  if (page.kind === 'redirect') {
    writeHtmlFile(outputFile, renderRedirectDocument(page))
    continue
  }

  if (page.kind === 'docs-index') {
    writeHtmlFile(outputFile, renderDocsIndexDocument(page))
    continue
  }

  const renderedPage = serverEntry.renderPage(page.pathname)
  writeHtmlFile(outputFile, renderDocument(renderedPage, clientEntry))
}

fs.rmSync(path.join(distDir, 'server'), { recursive: true, force: true })
