export default {
  meta: {
    description: 'Xiaomi Album Syncer - 全量 / 增量 / 定时下载小米云服务中的相册与录音到本地',
  },
  nav: {
    features: '功能',
    deploy: '部署',
    docs: '文档',
    theme: '切换主题',
    language: 'EN',
  },
  hero: {
    badge: '开源 · 自部署 · GPL-3.0',
    subtitle: '全量 / 增量 / 定时，把小米云服务中的相册与录音同步回本地存储。',
    download: '下载最新版本',
  },
  features: {
    title: '功能',
    items: [
      { title: '全量与增量下载', desc: '首次全量拉取相册，之后增量同步，自动跳过已下载的资产。' },
      { title: '定时任务', desc: '通过 Crontab 表达式调度同步计划，支持手动触发与执行历史回放。' },
      { title: '相册与录音', desc: '除相册资产外，同时支持下载小米云服务中的录音文件。' },
      { title: '多账号', desc: '支持绑定多个小米账号，扫码登录，Cookie 自动刷新续期。' },
      { title: '时间信息回填', desc: '下载后自动填充照片与视频的 Exif 时间及文件系统时间。' },
      { title: '路径表达式', desc: '用表达式插值自定义下载路径，按相册、日期等维度分类存储。' },
      { title: 'Web UI', desc: '友好的网页管理界面，支持 Passkey 登录与 SSL 部署。' },
      { title: '自部署', desc: 'Docker、原生二进制、JVM 三种部署方式，数据完全掌握在自己手里。' },
    ],
  },
  deploy: {
    title: '部署',
    binary: {
      title: '原生二进制',
      desc: '无需 Java 环境，前往 Releases 下载 Linux / macOS / Windows 对应平台的可执行文件。',
    },
    jvm: {
      title: 'JVM',
      desc: '与原生版本逻辑一致、数据库通用，适合已有 Java 运行时的环境。',
    },
  },
  footer: {
    license: 'Xiaomi Album Syncer · GPL-3.0 License',
    qq: 'QQ 群 1059332701',
  },
  docs: {
    title: '文档',
    menu: '目录',
    onThisPage: '本页内容',
    languageSwitch: '语言',
    index: {
      title: 'Xiaomi Album Syncer 文档',
      body: '请选择一种文档语言以浏览文档站点。',
      defaultTag: '默认',
      secondaryTag: '翻译',
    },
    notFound: {
      title: '页面不存在',
      body: '请求的页面不存在，请返回首页或前往文档入口。',
      backHome: '返回首页',
      openDocs: '查看文档',
    },
  },
}
