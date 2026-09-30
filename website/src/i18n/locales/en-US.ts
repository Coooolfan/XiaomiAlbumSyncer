export default {
  meta: {
    description:
      'Xiaomi Album Syncer - Full / incremental / scheduled download of albums and recordings from Xiaomi Cloud to local storage',
  },
  nav: {
    features: 'Features',
    deploy: 'Deploy',
    theme: 'Toggle theme',
    language: '中文',
  },
  hero: {
    badge: 'Open Source · Self-hosted · GPL-3.0',
    subtitle:
      'Full / incremental / scheduled sync of your Xiaomi Cloud albums and recordings back to local storage.',
    download: 'Download Latest',
  },
  features: {
    title: 'Features',
    items: [
      {
        title: 'Full & Incremental Download',
        desc: 'Pull the entire library on first run, then sync incrementally, skipping assets already downloaded.',
      },
      {
        title: 'Scheduled Tasks',
        desc: 'Schedule syncs with crontab expressions, with manual triggers and execution history replay.',
      },
      {
        title: 'Albums & Recordings',
        desc: 'In addition to album assets, download recordings stored in Xiaomi Cloud.',
      },
      {
        title: 'Multi-account',
        desc: 'Bind multiple Xiaomi accounts with QR-code login and automatic cookie refresh.',
      },
      {
        title: 'Timestamp Backfill',
        desc: 'Automatically fills in Exif and filesystem timestamps for photos and videos after download.',
      },
      {
        title: 'Path Expressions',
        desc: 'Customize download paths with expression interpolation, organized by album, date, and more.',
      },
      {
        title: 'Web UI',
        desc: 'A friendly web management interface with Passkey sign-in and SSL deployment support.',
      },
      {
        title: 'Self-hosted',
        desc: 'Docker, native binary, or JVM — your data stays entirely in your own hands.',
      },
    ],
  },
  deploy: {
    title: 'Deploy',
    binary: {
      title: 'Native Binary',
      desc: 'No Java runtime required — grab the executable for Linux / macOS / Windows from Releases.',
    },
    jvm: {
      title: 'JVM',
      desc: 'Same logic as the native build with a shared database — ideal if you already have a Java runtime.',
    },
  },
  footer: {
    license: 'Xiaomi Album Syncer · GPL-3.0 License',
    qq: 'QQ Group 1059332701',
  },
}
