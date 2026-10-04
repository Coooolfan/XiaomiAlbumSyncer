import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import HomePage from '../pages/HomePage'
import SiteNotFoundPage from '../pages/SiteNotFoundPage'
import DocsIndexPage from '../pages/DocsIndexPage'
import { DocsPage } from '../features/docs/DocsPage'
import { getDocEntry, isDocsLocale } from '../features/docs/registry'

function LocaleDocsPage() {
  const { locale, '*': slug = '' } = useParams()

  if (!locale || !isDocsLocale(locale)) {
    return <SiteNotFoundPage />
  }

  if (getDocEntry(locale, slug) === null) {
    return <Navigate replace to="/404" />
  }

  return <DocsPage locale={locale} />
}

export function WebsiteRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/docs" element={<DocsIndexPage />} />
      <Route path="/:locale/docs" element={<LocaleDocsPage />} />
      <Route path="/:locale/docs/*" element={<LocaleDocsPage />} />
      <Route path="/404" element={<SiteNotFoundPage />} />
      <Route path="*" element={<SiteNotFoundPage />} />
    </Routes>
  )
}
