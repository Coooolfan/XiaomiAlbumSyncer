import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Languages } from 'lucide-react'
import { docsLocaleLabels, getDocsRootHref, type DocsLocale } from '../features/docs/registry'

const LOCALES: readonly DocsLocale[] = ['zh-CN', 'en-US']

export default function DocsIndexPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen items-center justify-center bg-white px-6 text-black transition-colors duration-300 selection:bg-neutral-200 dark:bg-black dark:text-white dark:selection:bg-neutral-800">
      <div className="w-full max-w-3xl rounded-3xl border border-neutral-200 bg-white p-10 shadow-xl transition-colors duration-300 dark:border-neutral-900 dark:bg-neutral-950">
        <div className="mb-6 flex items-center gap-3 text-sm font-semibold tracking-[0.18em] text-neutral-500 uppercase">
          <Languages className="h-5 w-5" />
          Docs
        </div>
        <h1 className="mb-4 text-4xl font-semibold tracking-tight">{t('docs.index.title')}</h1>
        <p className="max-w-2xl text-base leading-8 text-neutral-600 dark:text-neutral-400">
          {t('docs.index.body')}
        </p>
        <div className="mt-10 grid gap-4 md:grid-cols-2">
          {LOCALES.map((docsLocale) => (
            <Link
              key={docsLocale}
              to={getDocsRootHref(docsLocale)}
              aria-label={docsLocaleLabels[docsLocale]}
              className="rounded-2xl border border-neutral-200 px-6 py-5 transition-colors duration-300 hover:border-neutral-300 hover:bg-neutral-50 dark:border-neutral-800 dark:hover:border-neutral-700 dark:hover:bg-neutral-900"
            >
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="text-lg font-semibold tracking-tight">{docsLocaleLabels[docsLocale]}</p>
                  <p className="mt-2 text-sm text-neutral-500 dark:text-neutral-400">
                    {docsLocale === 'zh-CN' ? t('docs.index.defaultTag') : t('docs.index.secondaryTag')}
                  </p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}
