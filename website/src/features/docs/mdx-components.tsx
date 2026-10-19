import { Children, cloneElement, isValidElement, type ComponentPropsWithoutRef, type HTMLAttributes, type ReactNode, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertCircle, Check, Copy, Info, Lightbulb, OctagonAlert, TriangleAlert } from 'lucide-react'

function mergeClassName(...values: Array<string | undefined>) {
  return values.filter(Boolean).join(' ')
}

function isExternalHref(href: string) {
  return href.startsWith('http://') || href.startsWith('https://')
}

function Heading({
  className,
  children,
  ...props
}: HTMLAttributes<HTMLHeadingElement> & { children?: ReactNode }) {
  return (
    <h2
      className={mergeClassName('mt-10 scroll-mt-28 text-2xl font-semibold tracking-tight text-(--xas-ink)', className)}
      {...props}
    >
      {children}
    </h2>
  )
}

type AlertType = 'NOTE' | 'TIP' | 'IMPORTANT' | 'WARNING' | 'CAUTION'

const ALERT_RE = /^\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\](?:\r?\n|[ \t]+)?/i

interface AlertConfig {
  icon: typeof Info
  titleZh: string
  titleEn: string
  containerClass: string
  headerClass: string
  iconClass: string
  bodyClass: string
}

const ALERT_CONFIG: Record<AlertType, AlertConfig> = {
  NOTE: {
    icon: Info,
    titleZh: '说明',
    titleEn: 'Note',
    containerClass:
      'border-l-4 border-l-blue-500 border-neutral-200/80 bg-blue-50/50 dark:border-neutral-800 dark:border-l-blue-400 dark:bg-blue-950/20',
    headerClass: 'text-blue-700 dark:text-blue-300',
    iconClass: 'text-blue-600 dark:text-blue-400',
    bodyClass: 'text-neutral-700 dark:text-neutral-300',
  },
  TIP: {
    icon: Lightbulb,
    titleZh: '提示',
    titleEn: 'Tip',
    containerClass:
      'border-l-4 border-l-emerald-500 border-neutral-200/80 bg-emerald-50/50 dark:border-neutral-800 dark:border-l-emerald-400 dark:bg-emerald-950/20',
    headerClass: 'text-emerald-700 dark:text-emerald-300',
    iconClass: 'text-emerald-600 dark:text-emerald-400',
    bodyClass: 'text-neutral-700 dark:text-neutral-300',
  },
  IMPORTANT: {
    icon: AlertCircle,
    titleZh: '重要',
    titleEn: 'Important',
    containerClass:
      'border-l-4 border-l-purple-500 border-neutral-200/80 bg-purple-50/50 dark:border-neutral-800 dark:border-l-purple-400 dark:bg-purple-950/20',
    headerClass: 'text-purple-700 dark:text-purple-300',
    iconClass: 'text-purple-600 dark:text-purple-400',
    bodyClass: 'text-neutral-700 dark:text-neutral-300',
  },
  WARNING: {
    icon: TriangleAlert,
    titleZh: '警告',
    titleEn: 'Warning',
    containerClass:
      'border-l-4 border-l-amber-500 border-neutral-200/80 bg-amber-50/50 dark:border-neutral-800 dark:border-l-amber-400 dark:bg-amber-950/20',
    headerClass: 'text-amber-800 dark:text-amber-300',
    iconClass: 'text-amber-600 dark:text-amber-400',
    bodyClass: 'text-neutral-700 dark:text-neutral-300',
  },
  CAUTION: {
    icon: OctagonAlert,
    titleZh: '注意',
    titleEn: 'Caution',
    containerClass:
      'border-l-4 border-l-rose-500 border-neutral-200/80 bg-rose-50/50 dark:border-neutral-800 dark:border-l-rose-400 dark:bg-rose-950/20',
    headerClass: 'text-rose-700 dark:text-rose-300',
    iconClass: 'text-rose-600 dark:text-rose-400',
    bodyClass: 'text-neutral-700 dark:text-neutral-300',
  },
}

function extractAlert(children: ReactNode): { type: AlertType; body: ReactNode[] } | null {
  const childArray = Children.toArray(children).filter(
    (c) => typeof c !== 'string' || c.trim() !== '',
  )
  if (!childArray.length) return null

  const first = childArray[0]

  if (typeof first === 'string') {
    const m = first.match(ALERT_RE)
    if (!m) return null
    const type = m[1].toUpperCase() as AlertType
    const rest = first.slice(m[0].length)
    const body: ReactNode[] = [rest, ...childArray.slice(1)].filter(
      (c) => typeof c !== 'string' || c.trim() !== '',
    )
    return { type, body }
  }

  if (isValidElement<{ children?: ReactNode }>(first)) {
    const pChildren = Children.toArray(first.props.children)
    const firstPChild = pChildren.find((c) => typeof c !== 'string' || c.trim() !== '')
    if (typeof firstPChild !== 'string') return null

    const m = firstPChild.match(ALERT_RE)
    if (!m) return null
    const type = m[1].toUpperCase() as AlertType
    const rest = firstPChild.slice(m[0].length)

    const firstPIndex = pChildren.indexOf(firstPChild)
    const remainingPChildren = pChildren.slice(firstPIndex + 1)

    let newFirst: ReactNode = null
    if (rest.trim() !== '' || remainingPChildren.length > 0) {
      newFirst = cloneElement(first, {
        ...first.props,
        children: rest.trim() !== '' ? [rest, ...remainingPChildren] : remainingPChildren,
      })
    }

    const body: ReactNode[] = newFirst
      ? [newFirst, ...childArray.slice(1)]
      : childArray.slice(1)
    return { type, body }
  }

  return null
}

function Blockquote({ className, children, ...props }: HTMLAttributes<HTMLQuoteElement>) {
  const { i18n } = useTranslation()
  const isZh = i18n.language?.startsWith('zh') ?? true
  const alert = extractAlert(children)

  if (alert) {
    const cfg = ALERT_CONFIG[alert.type]
    const Icon = cfg.icon
    const title = isZh ? cfg.titleZh : cfg.titleEn

    return (
      <div
        role="alert"
        className={mergeClassName(
          'my-6 rounded-r-lg border border-l-4 p-4 text-sm leading-7 shadow-xs transition-colors duration-200',
          cfg.containerClass,
          className,
        )}
      >
        <div className={mergeClassName('flex items-center gap-2 font-semibold tracking-wide', cfg.headerClass)}>
          <Icon className={mergeClassName('h-4 w-4 shrink-0', cfg.iconClass)} aria-hidden="true" />
          <span>{title}</span>
        </div>
        <div
          className={mergeClassName(
            'mt-2.5 space-y-2.5 [&_p]:m-0 [&_p]:leading-7 [&_p]:text-inherit [&_p]:text-[length:inherit] [&_ul]:my-2 [&_ul]:list-disc [&_ul]:pl-5 [&_ul]:text-inherit [&_ul]:text-[length:inherit] [&_ol]:my-2 [&_ol]:list-decimal [&_ol]:pl-5 [&_ol]:text-inherit [&_ol]:text-[length:inherit] [&_li]:pl-1 [&_li]:text-inherit [&_li]:text-[length:inherit]',
            cfg.bodyClass,
          )}
        >
          {alert.body}
        </div>
      </div>
    )
  }

  return (
    <blockquote
      className={mergeClassName(
        'mt-6 space-y-3 rounded border border-(--xas-blockquote-border) bg-(--xas-blockquote-bg) px-5 py-4 text-sm leading-7 text-(--xas-blockquote-text) [&_p]:m-0 [&_p]:text-inherit [&_p]:text-[length:inherit] [&_p]:leading-[inherit]',
        className,
      )}
      {...props}
    >
      {children}
    </blockquote>
  )
}

export const mdxComponents = {
  h1: ({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) => (
    <h1
      className={mergeClassName(
        'mb-4 scroll-mt-28 text-4xl font-semibold tracking-tight text-(--xas-ink) sm:text-5xl',
        className,
      )}
      {...props}
    />
  ),
  h2: Heading,
  h3: ({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) => (
    <h3
      className={mergeClassName('mt-8 scroll-mt-28 text-xl font-semibold tracking-tight text-(--xas-ink)', className)}
      {...props}
    />
  ),
  h4: ({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) => (
    <h4
      className={mergeClassName('mt-6 scroll-mt-28 text-lg font-semibold tracking-tight text-(--xas-ink)', className)}
      {...props}
    />
  ),
  p: ({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) => (
    <p className={mergeClassName('mt-5 text-base leading-8 text-(--xas-muted)', className)} {...props} />
  ),
  ul: ({ className, ...props }: HTMLAttributes<HTMLUListElement>) => (
    <ul className={mergeClassName('mt-5 list-disc space-y-3 pl-6 text-base leading-8 text-(--xas-muted)', className)} {...props} />
  ),
  ol: ({ className, ...props }: HTMLAttributes<HTMLOListElement>) => (
    <ol className={mergeClassName('mt-5 list-decimal space-y-3 pl-6 text-base leading-8 text-(--xas-muted)', className)} {...props} />
  ),
  li: ({ className, ...props }: HTMLAttributes<HTMLLIElement>) => (
    <li className={mergeClassName('pl-1', className)} {...props} />
  ),
  blockquote: Blockquote,
  hr: ({ className, ...props }: HTMLAttributes<HTMLHRElement>) => (
    <hr className={mergeClassName('my-10 border-(--xas-line)', className)} {...props} />
  ),
  a: ({
    className,
    href = '',
    ...props
  }: ComponentPropsWithoutRef<'a'>) => {
    const mergedClassName = mergeClassName(
      'font-medium text-(--xas-ink) underline decoration-(--xas-line) underline-offset-4 transition hover:decoration-(--xas-ink)',
      className,
    )

    if (href.startsWith('/')) {
      return <Link className={mergedClassName} to={href} {...props} />
    }

    if (isExternalHref(href)) {
      return <a className={mergedClassName} href={href} target="_blank" rel="noreferrer" {...props} />
    }

    return <a className={mergedClassName} href={href} {...props} />
  },
  code: ({ className, ...props }: ComponentPropsWithoutRef<'code'>) => (
    <code
      className={mergeClassName(
        'rounded-sm bg-(--xas-code-bg) px-1.5 py-0.5 font-mono text-[0.92em] text-(--xas-code-text)',
        className,
      )}
      {...props}
    />
  ),
  pre: ({ className, children, ...props }: ComponentPropsWithoutRef<'pre'>) => {
    const ref = useRef<HTMLPreElement>(null)
    const [copied, setCopied] = useState(false)

    const handleCopy = () => {
      const text = ref.current?.textContent ?? ''
      navigator.clipboard.writeText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    }

    return (
      <div className="group relative mt-6">
        <pre
          ref={ref}
          className={mergeClassName(
            'overflow-x-auto rounded border border-(--xas-line) bg-(--xas-pre-bg) px-5 py-4 text-sm leading-7 text-(--xas-pre-text) [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-inherit [&_code]:text-[length:inherit]',
            className,
          )}
          {...props}
        >
          {children}
        </pre>
        <button
          type="button"
          onClick={handleCopy}
          className="absolute top-2.5 right-2.5 rounded bg-(--xas-pre-bg) p-1.5 text-(--xas-pre-text) opacity-0 transition-opacity group-hover:opacity-70"
          aria-label="Copy code"
        >
          {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
        </button>
      </div>
    )
  },
  table: ({ className, ...props }: ComponentPropsWithoutRef<'table'>) => (
    <div className="mt-6 overflow-x-auto rounded border border-(--xas-table-border)">
      <table className={mergeClassName('min-w-full border-collapse text-left text-sm text-(--xas-table-text)', className)} {...props} />
    </div>
  ),
  thead: ({ className, ...props }: ComponentPropsWithoutRef<'thead'>) => (
    <thead className={mergeClassName('bg-(--xas-table-header-bg) text-(--xas-ink)', className)} {...props} />
  ),
  th: ({ className, ...props }: ComponentPropsWithoutRef<'th'>) => (
    <th className={mergeClassName('border-b border-(--xas-table-border) px-4 py-3 font-semibold', className)} {...props} />
  ),
  td: ({ className, ...props }: ComponentPropsWithoutRef<'td'>) => (
    <td className={mergeClassName('border-b border-(--xas-table-border) px-4 py-3 align-top', className)} {...props} />
  ),
  img: ({ className, alt = '', src = '', ...props }: ComponentPropsWithoutRef<'img'>) => {
    if (src && src.startsWith('/screenshots/') && !src.includes('-dark.')) {
      const darkSrc = src.replace(/\.([^.]+)$/, '-dark.$1')
      return (
        <>
          <img
            className={mergeClassName('mt-6 block dark:hidden rounded-xl border border-neutral-200/80 shadow-sm', className)}
            alt={alt}
            src={src}
            loading="lazy"
            {...props}
          />
          <img
            className={mergeClassName('mt-6 hidden dark:block rounded-xl border border-neutral-800/80 shadow-sm', className)}
            alt={alt}
            src={darkSrc}
            loading="lazy"
            {...props}
          />
        </>
      )
    }
    return (
      <img
        className={mergeClassName('mt-6 rounded-xl', className)}
        alt={alt}
        src={src}
        loading="lazy"
        {...props}
      />
    )
  },
}
