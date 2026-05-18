import { useAppStore } from '@/store/appStore'
import { Menu, LogOut, ChevronDown } from 'lucide-react'

const MODELS = [
  'deepseek-v4-pro', 'deepseek-chat', 'deepseek-reasoner',
  'gpt-4o', 'gpt-4-turbo', 'gpt-3.5-turbo',
  'qwen-max', 'qwen-plus',
  'glm-4',
]

export function Header() {
  const toggleSidebar = useAppStore((s) => s.toggleSidebar)
  const sidebarOpen = useAppStore((s) => s.sidebarOpen)
  const selectedModel = useAppStore((s) => s.selectedModel)
  const setSelectedModel = useAppStore((s) => s.setSelectedModel)
  const user = useAppStore((s) => s.user)
  const logout = useAppStore((s) => s.logout)

  return (
    <header className="h-14 border-b border-border bg-card/50 backdrop-blur flex items-center justify-between px-4">
      <div className="flex items-center gap-3">
        {!sidebarOpen && (
          <button onClick={toggleSidebar} className="p-1 rounded hover:bg-accent">
            <Menu className="w-5 h-5" />
          </button>
        )}
        <div className="relative">
          <select
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
            className="appearance-none bg-accent text-sm rounded-lg px-3 py-1.5 pr-8 cursor-pointer hover:bg-accent/80 focus:outline-none"
          >
            {MODELS.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
          <ChevronDown className="w-4 h-4 absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm text-muted-foreground">{user?.username || 'Admin'}</span>
        <button onClick={logout} className="p-1.5 rounded hover:bg-accent text-muted-foreground">
          <LogOut className="w-4 h-4" />
        </button>
      </div>
    </header>
  )
}
