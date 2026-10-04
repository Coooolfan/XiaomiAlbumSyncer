declare module '*.mdx' {
  import type { ComponentType } from 'react'
  import type { DocMeta } from './features/docs/registry'

  export const meta: DocMeta

  const MDXComponent: ComponentType<Record<string, unknown>>
  export default MDXComponent
}
