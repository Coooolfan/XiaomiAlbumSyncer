import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

function SiteNotFoundPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen items-center justify-center bg-white px-6 text-black transition-colors duration-300 dark:bg-black dark:text-white">
      <div className="w-full max-w-xl rounded-3xl border border-neutral-200 bg-white p-10 shadow-2xl transition-colors duration-300 dark:border-neutral-900 dark:bg-neutral-950 dark:shadow-black/30">
        <div className="mb-6 flex items-center gap-3 text-sm font-semibold tracking-[0.2em] text-neutral-500 uppercase dark:text-neutral-400">
          <img src="/logo.png" alt="Xiaomi Album Syncer" className="h-5 w-5" />
          Xiaomi Album Syncer
        </div>
        <p className="mb-2 text-sm font-medium tracking-[0.18em] text-neutral-400 uppercase dark:text-neutral-500">
          404
        </p>
        <h1 className="mb-4 text-4xl font-semibold tracking-tight">{t('docs.notFound.title')}</h1>
        <p className="mb-8 max-w-lg text-base leading-7 text-neutral-600 dark:text-neutral-300">
          {t('docs.notFound.body')}
        </p>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link
            to="/"
            className="inline-flex items-center justify-center rounded-xl bg-black px-5 py-3 font-medium text-white transition hover:bg-neutral-700 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
          >
            {t('docs.notFound.backHome')}
          </Link>
          <Link
            to="/docs"
            className="inline-flex items-center justify-center rounded-xl border border-black/15 px-5 py-3 font-medium text-black transition hover:bg-black/5 dark:border-white/15 dark:text-white dark:hover:bg-white/8"
          >
            {t('docs.notFound.openDocs')}
          </Link>
        </div>
      </div>
    </div>
  )
}

export default SiteNotFoundPage
