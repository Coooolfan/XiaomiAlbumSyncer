import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { SeoManager } from './SeoManager'
import { WebsiteRoutes } from './routes'

function ScrollToTop() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    if (hash) {
      const frame = requestAnimationFrame(() => {
        const id = decodeURIComponent(hash.slice(1))
        const el = document.getElementById(id)
        if (el) {
          el.scrollIntoView()
        }
      })
      return () => cancelAnimationFrame(frame)
    }

    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [pathname, hash])

  return null
}

export function WebsiteApp() {
  return (
    <>
      <ScrollToTop />
      <SeoManager />
      <WebsiteRoutes />
    </>
  )
}
