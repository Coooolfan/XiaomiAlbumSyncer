import { type ComponentPropsWithoutRef, type HTMLAttributes, type ReactNode, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, Copy } from 'lucide-react'

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
  blockquote: ({ className, ...props }: HTMLAttributes<HTMLQuoteElement>) => (
    <blockquote
      className={mergeClassName(
        'mt-6 space-y-3 rounded border border-(--xas-blockquote-border) bg-(--xas-blockquote-bg) px-5 py-4 text-sm leading-7 text-(--xas-blockquote-text) [&_p]:m-0 [&_p]:text-inherit [&_p]:text-[length:inherit] [&_p]:leading-[inherit]',
        className,
      )}
      {...props}
    />
  ),
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
  img: ({ className, alt = '', ...props }: ComponentPropsWithoutRef<'img'>) => (
    <img
      className={mergeClassName('mt-6', className)}
      alt={alt}
      loading="lazy"
      {...props}
    />
  ),
}
