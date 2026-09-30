import Dither from '@/components/Dither'

const GITHUB_URL = 'https://github.com/Coooolfan/XiaomiAlbumSyncer'
const RELEASES_URL = `${GITHUB_URL}/releases`
const DOCKER_URL = 'https://hub.docker.com/r/coolfan1024/xiaomi-album-syncer'
const QQ_URL = 'https://qm.qq.com/q/H2trW6JWM4'

const features = [
  {
    title: '全量与增量下载',
    desc: '首次全量拉取相册，之后增量同步，自动跳过已下载的资产。',
  },
  {
    title: '定时任务',
    desc: '通过 Crontab 表达式调度同步计划，支持手动触发与执行历史回放。',
  },
  {
    title: '相册与录音',
    desc: '除相册资产外，同时支持下载小米云服务中的录音文件。',
  },
  {
    title: '多账号',
    desc: '支持绑定多个小米账号，扫码登录，Cookie 自动刷新续期。',
  },
  {
    title: '时间信息回填',
    desc: '下载后自动填充照片与视频的 Exif 时间及文件系统时间。',
  },
  {
    title: '路径表达式',
    desc: '用表达式插值自定义下载路径，按相册、日期等维度分类存储。',
  },
  {
    title: 'Web UI',
    desc: '友好的网页管理界面，支持 Passkey 登录与 SSL 部署。',
  },
  {
    title: '自部署',
    desc: 'Docker、原生二进制、JVM 三种部署方式，数据完全掌握在自己手里。',
  },
]

const dockerCommand = `docker run -d \\
  -p 8232:8080 \\
  --name xiaomi-album-syncer \\
  -v ~/xiaomi-album-syncer/download:/app/download \\
  -v ~/xiaomi-album-syncer/db:/app/db \\
  coolfan1024/xiaomi-album-syncer:latest`

export default function App() {
  return (
    <div className="relative min-h-screen">
      {/* 背景：ReactBits Dither */}
      <div className="fixed inset-0 -z-10">
        <Dither
          waveSpeed={0.03}
          waveFrequency={1.5}
          waveColor={[0.5137254901960784, 0.5137254901960784, 0.5137254901960784]}
          colorNum={3}
          enableMouseInteraction={false}
          mouseRadius={0}
        />
      </div>
      <div className="fixed inset-0 -z-10 bg-black/40" />

      {/* 导航 */}
      <header className="fixed inset-x-0 top-0 z-10 border-b border-white/10 bg-black/40 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-6">
          <a href="#" className="flex items-center gap-2.5">
            <img src="/logo.png" alt="logo" className="h-7 w-7" />
            <span className="font-mono text-sm font-semibold tracking-wide">
              Xiaomi Album Syncer
            </span>
          </a>
          <nav className="flex items-center gap-6 text-sm text-slate-300">
            <a href="#features" className="hidden transition-colors hover:text-white sm:inline">
              功能
            </a>
            <a href="#deploy" className="hidden transition-colors hover:text-white sm:inline">
              部署
            </a>
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noreferrer"
              className="rounded-md border border-white/20 px-3 py-1.5 font-mono text-xs text-white transition-colors hover:bg-white/10"
            >
              GitHub
            </a>
          </nav>
        </div>
      </header>

      {/* Hero */}
      <section className="mx-auto flex min-h-screen max-w-5xl flex-col items-center justify-center px-6 pt-14 text-center">
        <p className="mb-6 rounded-full border border-white/25 bg-black/50 px-4 py-1 font-mono text-xs tracking-widest text-slate-200 backdrop-blur-sm">
          开源 · 自部署 · GPL-3.0
        </p>
        <h1 className="font-mono text-3xl leading-tight font-bold tracking-tight whitespace-nowrap text-white sm:text-5xl lg:text-6xl">
          Xiaomi Album Syncer
        </h1>
        <p className="mt-6 max-w-xl text-base leading-relaxed text-slate-300 sm:text-lg">
          全量 / 增量 / 定时，把小米云服务中的相册与录音同步回本地存储。
        </p>
        <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
          <a
            href={RELEASES_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md bg-white px-6 py-3 font-mono text-sm font-semibold text-black transition-colors hover:bg-slate-200"
          >
            下载最新版本
          </a>
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-white/25 px-6 py-3 font-mono text-sm text-white transition-colors hover:bg-white/10"
          >
            GitHub →
          </a>
          <a
            href={DOCKER_URL}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-white/25 px-6 py-3 font-mono text-sm text-white transition-colors hover:bg-white/10"
          >
            Docker Hub →
          </a>
        </div>
      </section>

      {/* 界面预览 */}
      <section className="mx-auto max-w-5xl px-6 pb-24">
        <div className="overflow-hidden rounded-xl border border-white/15 shadow-2xl shadow-black/60">
          <img src="/banner.avif" alt="Xiaomi Album Syncer Web UI" className="w-full" />
        </div>
      </section>

      {/* 功能 */}
      <section id="features" className="mx-auto max-w-5xl scroll-mt-20 px-6 pb-24">
        <h2 className="mb-10 text-center font-mono text-2xl font-bold text-white sm:text-3xl">
          功能
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <div
              key={f.title}
              className="rounded-lg border border-white/10 bg-black/50 p-5 backdrop-blur-sm transition-colors hover:border-white/25"
            >
              <h3 className="mb-2 font-mono text-sm font-semibold text-white">{f.title}</h3>
              <p className="text-sm leading-relaxed text-slate-400">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* 部署 */}
      <section id="deploy" className="mx-auto max-w-5xl scroll-mt-20 px-6 pb-24">
        <h2 className="mb-10 text-center font-mono text-2xl font-bold text-white sm:text-3xl">
          部署
        </h2>
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="rounded-lg border border-white/10 bg-black/50 p-5 backdrop-blur-sm lg:col-span-2">
            <h3 className="mb-3 font-mono text-sm font-semibold text-white">Docker</h3>
            <pre className="overflow-x-auto rounded-md border border-white/10 bg-black/60 p-4 font-mono text-xs leading-relaxed text-slate-300">
              {dockerCommand}
            </pre>
          </div>
          <div className="flex flex-col gap-4">
            <div className="flex-1 rounded-lg border border-white/10 bg-black/50 p-5 backdrop-blur-sm">
              <h3 className="mb-2 font-mono text-sm font-semibold text-white">原生二进制</h3>
              <p className="text-sm leading-relaxed text-slate-400">
                无需 Java 环境，前往 Releases 下载 Linux / macOS / Windows 对应平台的可执行文件。
              </p>
            </div>
            <div className="flex-1 rounded-lg border border-white/10 bg-black/50 p-5 backdrop-blur-sm">
              <h3 className="mb-2 font-mono text-sm font-semibold text-white">JVM</h3>
              <p className="text-sm leading-relaxed text-slate-400">
                与原生版本逻辑一致、数据库通用，适合已有 Java 运行时的环境。
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 页脚 */}
      <footer className="border-t border-white/10">
        <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-4 px-6 py-8 text-sm text-slate-400 sm:flex-row">
          <p className="font-mono text-xs">Xiaomi Album Syncer · GPL-3.0 License</p>
          <div className="flex items-center gap-6">
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-white"
            >
              GitHub
            </a>
            <a
              href={QQ_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-white"
            >
              QQ 群 1059332701
            </a>
            <a
              href={DOCKER_URL}
              target="_blank"
              rel="noreferrer"
              className="transition-colors hover:text-white"
            >
              Docker Hub
            </a>
          </div>
        </div>
      </footer>
    </div>
  )
}
