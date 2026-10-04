import { renderToString } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'
import { WebsiteApp } from './app/WebsiteApp'
import { getLegacyRedirect, listPrerenderPaths } from './app/routing'
import { getSeoForPath } from './app/seo'

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

export interface StaticPage {
  kind: 'page'
  pathname: string
}

export function getPrerenderPages(): Array<RedirectPage | StaticPage> {
  return listPrerenderPaths().map((pathname) => {
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
