import { renderToString } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'
import { WebsiteApp } from './app/WebsiteApp'
import { defaultDocsLocale, getLegacyRedirect, listPrerenderPaths } from './app/routing'
import { getSeoForPath } from './app/seo'
import { getDocsRootHref } from './features/docs/registry'

export interface RenderedPage {
  appHtml: string
  pathname: string
  seo: ReturnType<typeof getSeoForPath>
}

export interface RedirectPage {
  kind: 'redirect'
  pathname: string
  redirectTo: string
  seo: ReturnType<typeof getSeoForPath>
}

export interface DocsIndexPage {
  kind: 'docs-index'
  pathname: string
  defaultHref: string
  seo: ReturnType<typeof getSeoForPath>
}

export interface StaticPage {
  kind: 'page'
  pathname: string
}

export function getPrerenderPages(): Array<RedirectPage | DocsIndexPage | StaticPage> {
  return listPrerenderPaths().map((pathname) => {
    if (pathname === '/docs') {
      return {
        kind: 'docs-index' as const,
        pathname,
        defaultHref: getDocsRootHref(defaultDocsLocale),
        seo: getSeoForPath(pathname),
      }
    }

    const redirect = getLegacyRedirect(pathname)

    if (redirect) {
      return {
        kind: 'redirect' as const,
        pathname,
        redirectTo: redirect.to,
        seo: getSeoForPath(pathname),
      }
    }

    return {
      kind: 'page' as const,
      pathname,
    }
  })
}

export function renderPage(pathname: string): RenderedPage {
  return {
    appHtml: renderToString(
      <StaticRouter location={pathname}>
        <WebsiteApp />
      </StaticRouter>,
    ),
    pathname,
    seo: getSeoForPath(pathname),
  }
}
