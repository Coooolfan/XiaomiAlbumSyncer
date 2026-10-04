import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { applySeoToDocument, getSeoForPath } from './seo'

export function SeoManager() {
  const location = useLocation()

  useEffect(() => {
    applySeoToDocument(getSeoForPath(location.pathname))
  }, [location.pathname])

  return null
}
