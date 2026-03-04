'use client'

import { useEffect, useState } from 'react'
import { useTheme } from 'next-themes'
import { Monitor, Moon, Sun } from 'lucide-react'

export function ThemeToggle() {
  const { theme, resolvedTheme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  const effectiveTheme = resolvedTheme ?? theme ?? 'light'
  const isDark = effectiveTheme === 'dark'

  const toggle = () => {
    setTheme(isDark ? 'light' : 'dark')
  }

  // Avoid hydration mismatches: on the server (and on the first client render),
  // next-themes may not have resolved the actual theme yet.
  if (!mounted) {
    return (
      <button
        type="button"
        className="relative p-2 hover:bg-secondary rounded-lg transition-colors"
        aria-label="Toggle theme"
        title="Toggle theme"
        disabled
      >
        <Monitor size={20} className="text-foreground" />
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={toggle}
      className="relative p-2 hover:bg-secondary rounded-lg transition-colors"
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={isDark ? 'Light mode' : 'Dark mode'}
    >
      {isDark ? (
        <Sun size={20} className="text-foreground" />
      ) : (
        <Moon size={20} className="text-foreground" />
      )}
    </button>
  )
}
