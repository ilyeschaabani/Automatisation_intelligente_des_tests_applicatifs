'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  LayoutDashboard,
  Folder,
  PlayCircle,
  BarChart3,
  FileText,
  Settings,
  Users,
  LogOut,
  Menu,
  X,
  Zap,
  Brain,
  Shield,
} from 'lucide-react'
import { useState } from 'react'
import { cn } from '@/lib/utils'

export function Sidebar() {
  const pathname = usePathname()
  const [isOpen, setIsOpen] = useState(true)

  const menuItems = [
    {
      label: 'Dashboard',
      href: '/dashboard',
      icon: LayoutDashboard,
    },
    {
      label: 'Projects',
      href: '/projects',
      icon: Folder,
    },
    {
      label: 'Test Campaigns',
      href: '/campaigns',
      icon: PlayCircle,
    },
    {
      label: 'Test Executions',
      href: '/executions',
      icon: Zap,
    },
    {
      label: 'Test Results',
      href: '/results',
      icon: BarChart3,
    },
    {
      label: 'Intelligence',
      href: '/intelligence',
      icon: Brain,
    },
    {
      label: 'Security',
      href: '/security',
      icon: Shield,
    },
    {
      label: 'Reports',
      href: '/reports',
      icon: FileText,
    },
    {
      label: 'Users & Roles',
      href: '/users',
      icon: Users,
    },
    {
      label: 'Settings',
      href: '/settings',
      icon: Settings,
    },
  ]

  return (
    <>
      {/* Mobile Toggle */}
      <div className="fixed top-0 left-0 right-0 z-40 lg:hidden bg-card border-b border-border p-4 flex items-center justify-between">
        <h1 className="text-lg font-bold text-primary">TestAuto</h1>
        <button
          onClick={() => setIsOpen(!isOpen)}
          className="p-2 hover:bg-secondary rounded-lg"
        >
          {isOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed left-0 top-0 bottom-0 w-64 bg-sidebar text-sidebar-foreground border-r border-sidebar-border transition-all duration-300 flex flex-col z-30',
          'lg:sticky lg:top-0 lg:h-screen lg:w-64',
          !isOpen && 'lg:hidden -translate-x-full'
        )}
      >
        {/* Logo */}
        <div className="p-6 border-b border-sidebar-border">
          <h1 className="text-xl font-bold bg-gradient-to-r from-sidebar-primary to-accent bg-clip-text text-transparent">
            TestAuto
          </h1>
          <p className="text-xs text-sidebar-foreground/60 mt-1">
            Banking Test Platform
          </p>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto py-6 px-3">
          <ul className="space-y-2">
            {menuItems.map((item) => {
              const Icon = item.icon
              const isActive = pathname === item.href
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    className={cn(
                      'flex items-center gap-3 px-4 py-3 rounded-lg transition-all',
                      isActive
                        ? 'bg-sidebar-primary text-sidebar-primary-foreground'
                        : 'text-sidebar-foreground hover:bg-sidebar-accent'
                    )}
                  >
                    <Icon size={20} />
                    <span className="font-medium">{item.label}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>

        {/* Footer */}
        <div className="border-t border-sidebar-border p-4 space-y-3">
          <button className="w-full flex items-center gap-3 px-4 py-2 rounded-lg text-sidebar-foreground hover:bg-sidebar-accent transition-all">
            <LogOut size={18} />
            <span className="text-sm font-medium">Sign Out</span>
          </button>
          <div className="text-xs text-sidebar-foreground/50 px-4 py-2">
            v1.0.0 • © 2025
          </div>
        </div>
      </aside>

      {/* Mobile Overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-20 lg:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}
    </>
  )
}
