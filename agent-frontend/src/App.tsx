import { Routes, Route, Navigate } from 'react-router-dom'
import { Sidebar } from '@/components/layout/Sidebar'
import { Header } from '@/components/layout/Header'
import { useAppStore } from '@/store/appStore'
import Dashboard from '@/pages/Dashboard'
import Chat from '@/pages/Chat'
import Knowledge from '@/pages/Knowledge'
import Workflow from '@/pages/Workflow'
import Monitor from '@/pages/Monitor'
import Settings from '@/pages/Settings'
import Login from '@/pages/Login'
import Register from '@/pages/Register'

export default function App() {
  const token = useAppStore((s) => s.token)
  const sidebarOpen = useAppStore((s) => s.sidebarOpen)

  if (!token) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />
      <div className={`flex-1 flex flex-col transition-all duration-300 ${sidebarOpen ? 'ml-64' : 'ml-0'}`}>
        <Header />
        <main className="flex-1 overflow-auto p-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/chat" element={<Chat />} />
            <Route path="/knowledge" element={<Knowledge />} />
            <Route path="/workflow" element={<Workflow />} />
            <Route path="/monitor" element={<Monitor />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}
