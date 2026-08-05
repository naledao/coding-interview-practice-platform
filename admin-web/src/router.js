import { useEffect, useState } from 'react'

function normalizedHash() {
  const value = window.location.hash.replace(/^#/, '')
  return value.startsWith('/') ? value : '/overview'
}

export function navigate(path, { replace = false } = {}) {
  const nextHash = `#${path.startsWith('/') ? path : `/${path}`}`
  if (replace) {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${nextHash}`)
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    return
  }
  window.location.hash = nextHash
}

export function useRoute() {
  const [route, setRoute] = useState(normalizedHash)

  useEffect(() => {
    const update = () => setRoute(normalizedHash())
    window.addEventListener('hashchange', update)
    return () => window.removeEventListener('hashchange', update)
  }, [])

  return route
}

export function routeId(route, prefix) {
  const match = route.match(new RegExp(`^/${prefix}/(\\d+)$`))
  return match?.[1] || null
}
