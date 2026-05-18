import { NavLink } from 'react-router-dom'
import { useAppStore } from '@/store/appStore'
import {
  LayoutDashboard, MessageSquare, BookOpen, GitBranch,
  BarChart3, Settings, Bot, X
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/chat', icon: MessageSquare, label: 'Chat' },
  { to: '/knowledge', icon: BookOpen, label: 'Knowledge' },
  { to: '/workflow', icon: GitBranch, label: 'Workflow' },
  { to: '/monitor', icon: BarChart3, label: 'Monitor' },
  { to: '/settings', icon: Settings, label: 'Settings' },
]

export function Sidebar() {
  const sidebarOpen = useAppStore((s) => s.sidebarOpen)
  const toggleSidebar = useAppStore((s) => s.toggleSidebar)

  if (!sidebarOpen) return null

  return (
    <aside className="fixed left-0 top-0 h-full w-64 bg-card border-r border-border z-40 flex flex-col">
      <div className="flex items-center justify-between px-4 py-4 border-b border-border">
        <div className="flex items-center gap-2">
          <Bot className="w-6 h-6 text-primary" />
          <span className="font-bold text-lg">Agent Platform</span>
        </div>
        <button onClick={toggleSidebar} className="p-1 rounded hover:bg-accent">
          <X className="w-4 h-4" />
        </button>
      </div>
      <nav className="flex-1 py-4 space-y-1 px-3">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors',
                isActive
                  ? 'bg-primary/10 text-primary font-medium'
                  : 'text-muted-foreground hover:bg-accent hover:text-foreground'
              )
            }
          >
            <Icon className="w-5 h-5" />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
