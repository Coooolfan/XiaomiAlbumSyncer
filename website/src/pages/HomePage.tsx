import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Dither from '@/components/Dither'
import { useTheme } from '@/hooks/useTheme'
import { setLocale, type AppLocale } from '@/i18n'

const GITHUB_URL = 'https://github.com/Coooolfan/XiaomiAlbumSyncer'
const RELEASES_URL = `${GITHUB_URL}/releases`
const DOCKER_URL = 'https://hub.docker.com/r/coolfan1024/xiaomi-album-syncer'
const QQ_URL = 'https://qm.qq.com/q/H2trW6JWM4'

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

function GithubIcon({ className = 'h-4 w-4' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
      />
    </svg>
  )
}

export default function HomePage() {
  const { t, i18n } = useTranslation()
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  const nextLocale: AppLocale = i18n.language === 'zh-CN' ? 'en-US' : 'zh-CN'

  return (
    <div className="relative flex min-h-screen flex-col justify-between">
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

      {/* 导航：对齐 web 端 sidebar 质感 */}
      <header className="fixed inset-x-0 top-0 z-10 border-b border-slate-200/80 bg-white/60 backdrop-blur-xl dark:border-slate-800/80 dark:bg-slate-900/50">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5">
            <img src="/logo.png" alt="logo" className="h-[28px] w-[28px] object-contain" />
            <span className="text-[15px] font-semibold tracking-tight text-slate-800 dark:text-slate-100">
              Xiaomi Album Syncer
            </span>
          </Link>
          <nav className="flex items-center gap-1 text-[14px] text-slate-600 sm:gap-2 dark:text-slate-400">
            <Link
              to="/docs"
              className="rounded-md px-2.5 py-1.5 transition-colors hover:bg-slate-200/50 hover:text-slate-900 dark:hover:bg-slate-800/60 dark:hover:text-slate-100"
            >
              {t('nav.docs')}
            </Link>
            <button
              type="button"
              onClick={() => setLocale(nextLocale)}
              className="rounded-md px-2.5 py-1.5 font-mono text-[13px] transition-colors hover:bg-slate-200/50 hover:text-slate-900 dark:hover:bg-slate-800/60 dark:hover:text-slate-100"
            >
              {t('nav.language')}
            </button>
            <button
              type="button"
              onClick={toggle}
              aria-label={t('nav.theme')}
              className="rounded-md p-2 transition-colors hover:bg-slate-200/50 hover:text-slate-900 dark:hover:bg-slate-800/60 dark:hover:text-slate-100"
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
              className="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 transition-colors hover:bg-slate-200/50 hover:text-slate-900 dark:hover:bg-slate-800/60 dark:hover:text-slate-100"
            >
              <GithubIcon className="h-4 w-4" />
              <span>GitHub</span>
            </a>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col items-center justify-center px-6 pt-16 pb-6 text-center">
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
            className="rounded-md border border-black/20 bg-white/80 px-6 py-3 font-mono text-sm text-slate-900 backdrop-blur-md transition-colors hover:bg-white hover:border-black/35 dark:border-white/20 dark:bg-black/60 dark:text-white dark:hover:bg-black/85 dark:hover:border-white/35"
          >
            GitHub →
          </a>
          <Link
            to="/docs"
            className="rounded-md border border-black/20 bg-white/80 px-6 py-3 font-mono text-sm text-slate-900 backdrop-blur-md transition-colors hover:bg-white hover:border-black/35 dark:border-white/20 dark:bg-black/60 dark:text-white dark:hover:bg-black/85 dark:hover:border-white/35"
          >
            {t('nav.docs')} →
          </Link>
        </div>
      </main>

      {/* 页脚：对齐 web 端 sidebar 底部质感 */}
      <footer className="border-t border-slate-200/80 bg-white/60 backdrop-blur-xl dark:border-slate-800/80 dark:bg-slate-900/50">
        <div className="mx-auto flex h-11 max-w-5xl items-center justify-between gap-4 px-4 sm:px-6 text-xs text-slate-500 dark:text-slate-400">
          <p className="font-mono text-xs">{t('footer.license')}</p>
          <div className="flex items-center gap-4 sm:gap-5 text-xs">
            <a
              href={QQ_URL}
              target="_blank"
              rel="noreferrer"
              className="text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            >
              {t('footer.qq')}
            </a>
            <span className="text-slate-300 dark:text-slate-700">·</span>
            <a
              href={DOCKER_URL}
              target="_blank"
              rel="noreferrer"
              className="text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            >
              Docker Hub
            </a>
            <span className="text-slate-300 dark:text-slate-700">·</span>
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noreferrer"
              className="text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
              aria-label="GitHub"
            >
              <GithubIcon className="h-3.5 w-3.5" />
            </a>
          </div>
        </div>
      </footer>
    </div>
  )
}
