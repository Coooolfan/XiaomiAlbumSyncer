import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Dither from '@/components/Dither'
import { useTheme } from '@/hooks/useTheme'
import { setLocale, type AppLocale } from '@/i18n'

const GITHUB_URL = 'https://github.com/Coooolfan/XiaomiAlbumSyncer'
const RELEASES_URL = `${GITHUB_URL}/releases`
const DOCKER_URL = 'https://hub.docker.com/r/coolfan1024/xiaomi-album-syncer'
const QQ_URL = 'https://qm.qq.com/q/H2trW6JWM4'

const dockerCommand = `docker run -d \\
  -p 8232:8080 \\
  --name xiaomi-album-syncer \\
  -v ~/xiaomi-album-syncer/download:/app/download \\
  -v ~/xiaomi-album-syncer/db:/app/db \\
  coolfan1024/xiaomi-album-syncer:latest`

interface FeatureItem {
  title: string
  desc: string
}

function SunIcon() {
  return (
    <svg
      className="h-4 w-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32 1.41 1.41M2 12h2m16 0h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg
      className="h-4 w-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  )
}

export default function HomePage() {
  const { t, i18n } = useTranslation()
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  const features = t('features.items', { returnObjects: true }) as FeatureItem[]
  const nextLocale: AppLocale = i18n.language === 'zh-CN' ? 'en-US' : 'zh-CN'

  return (
    <div className="relative min-h-screen">
      {/* 背景：ReactBits Dither */}
      <div className="fixed inset-0 -z-10">
        <Dither
          waveSpeed={0.03}
          waveFrequency={1.5}
          waveColor={[0.5137254901960784, 0.5137254901960784, 0.5137254901960784]}
          backgroundColor={dark ? [0, 0, 0] : [1, 1, 1]}
          colorNum={3}
          enableMouseInteraction={false}
          mouseRadius={0}
        />
      </div>
      <div className="fixed inset-0 -z-10 bg-white/45 dark:bg-black/40" />

      {/* 导航 */}
      <header className="fixed inset-x-0 top-0 z-10 border-b border-black/10 bg-white/70 backdrop-blur-md dark:border-white/10 dark:bg-black/40">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
          <a href="#" className="flex items-center gap-2.5">
            <img src="/logo.png" alt="logo" className="h-7 w-7" />
            <span className="font-mono text-sm font-semibold tracking-wide text-slate-900 dark:text-white">
              Xiaomi Album Syncer
            </span>
          </a>
          <nav className="flex items-center gap-2 text-sm text-slate-600 sm:gap-6 dark:text-slate-300">
            <a
              href="#features"
              className="hidden transition-colors hover:text-black sm:inline dark:hover:text-white"
            >
              {t('nav.features')}
            </a>
            <a
              href="#deploy"
              className="hidden transition-colors hover:text-black sm:inline dark:hover:text-white"
            >
              {t('nav.deploy')}
            </a>
            <Link
              to="/docs"
              className="hidden transition-colors hover:text-black sm:inline dark:hover:text-white"
            >
              {t('nav.docs')}
            </Link>
            <button
              type="button"
              onClick={() => setLocale(nextLocale)}
              className="rounded-md px-2 py-1.5 font-mono text-xs transition-colors hover:bg-black/5 hover:text-black dark:hover:bg-white/10 dark:hover:text-white"
            >
              {t('nav.language')}
            </button>
            <button
              type="button"
              onClick={toggle}
              aria-label={t('nav.theme')}
              className="rounded-md p-1.5 transition-colors hover:bg-black/5 hover:text-black dark:hover:bg-white/10 dark:hover:text-white"
            >
              <span className="hidden dark:block">
                <SunIcon />
              </span>
              <span className="dark:hidden">
                <MoonIcon />
              </span>
            </button>
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noreferrer"
              className="rounded-md border border-black/20 px-3 py-1.5 font-mono text-xs text-slate-900 transition-colors hover:bg-black/5 dark:border-white/20 dark:text-white dark:hover:bg-white/10"
            >
              GitHub
            </a>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <section className="mx-auto flex min-h-screen max-w-5xl flex-col items-center justify-center px-6 pt-14 text-center">
        <p className="mb-6 rounded-full border border-black/20 bg-white/60 px-4 py-1 font-mono text-xs tracking-widest text-slate-700 backdrop-blur-sm dark:border-white/25 dark:bg-black/50 dark:text-slate-200">
          {t('hero.badge')}
        </p>
        <h1 className="font-mono text-3xl leading-tight font-bold tracking-tight text-balance text-slate-900 sm:text-5xl lg:text-6xl dark:text-white">
          Xiaomi Album Syncer
        </h1>
        <p className="mt-6 max-w-xl text-base leading-relaxed text-slate-600 sm:text-lg dark:text-slate-300">
          {t('hero.subtitle')}
        </p>
        <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
          <a
            href={RELEASES_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md bg-slate-900 px-6 py-3 font-mono text-sm font-semibold text-white transition-colors hover:bg-slate-700 dark:bg-white dark:text-black dark:hover:bg-slate-200"
          >
            {t('hero.download')}
          </a>
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-black/25 px-6 py-3 font-mono text-sm text-slate-900 transition-colors hover:bg-black/5 dark:border-white/25 dark:text-white dark:hover:bg-white/10"
          >
            GitHub →
          </a>
          <a
            href={DOCKER_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-black/25 px-6 py-3 font-mono text-sm text-slate-900 transition-colors hover:bg-black/5 dark:border-white/25 dark:text-white dark:hover:bg-white/10"
          >
            Docker Hub →
          </a>
        </div>
      </section>

      {/* 功能 */}
      <section id="features" className="mx-auto max-w-5xl scroll-mt-20 px-6 pb-24">
        <h2 className="mb-10 text-center font-mono text-2xl font-bold text-slate-900 sm:text-3xl dark:text-white">
          {t('features.title')}
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <div
              key={f.title}
              className="rounded-lg border border-black/10 bg-white/60 p-5 backdrop-blur-sm transition-colors hover:border-black/25 dark:border-white/10 dark:bg-black/50 dark:hover:border-white/25"
            >
              <h3 className="mb-2 font-mono text-sm font-semibold text-slate-900 dark:text-white">
                {f.title}
              </h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 部署 */}
      <section id="deploy" className="mx-auto max-w-5xl scroll-mt-20 px-6 pb-24">
        <h2 className="mb-10 text-center font-mono text-2xl font-bold text-slate-900 sm:text-3xl dark:text-white">
          {t('deploy.title')}
        </h2>
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="rounded-lg border border-black/10 bg-white/60 p-5 backdrop-blur-sm lg:col-span-2 dark:border-white/10 dark:bg-black/50">
            <h3 className="mb-3 font-mono text-sm font-semibold text-slate-900 dark:text-white">
              Docker
            </h3>
            <pre className="overflow-x-auto rounded-md border border-black/10 bg-black/5 p-4 font-mono text-xs leading-relaxed text-slate-700 dark:border-white/10 dark:bg-black/60 dark:text-slate-300">
              {dockerCommand}
            </pre>
          </div>
          <div className="flex flex-col gap-4">
            <div className="flex-1 rounded-lg border border-black/10 bg-white/60 p-5 backdrop-blur-sm dark:border-white/10 dark:bg-black/50">
              <h3 className="mb-2 font-mono text-sm font-semibold text-slate-900 dark:text-white">
                {t('deploy.binary.title')}
              </h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                {t('deploy.binary.desc')}
              </p>
            </div>
            <div className="flex-1 rounded-lg border border-black/10 bg-white/60 p-5 backdrop-blur-sm dark:border-white/10 dark:bg-black/50">
              <h3 className="mb-2 font-mono text-sm font-semibold text-slate-900 dark:text-white">
                {t('deploy.jvm.title')}
              </h3>
              <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-400">
                {t('deploy.jvm.desc')}
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 页脚 */}
      <footer className="border-t border-black/10 dark:border-white/10">
        <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-4 px-6 py-8 text-sm text-slate-500 sm:flex-row dark:text-slate-400">
          <p className="font-mono text-xs">{t('footer.license')}</p>
          <div className="flex items-center gap-6">
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-black dark:hover:text-white"
            >
              GitHub
            </a>
            <a
              href={QQ_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-black dark:hover:text-white"
            >
              {t('footer.qq')}
            </a>
            <a
              href={DOCKER_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-black dark:hover:text-white"
            >
              Docker Hub
            </a>
          </div>
        </div>
      </footer>
    </div>
  )
}
