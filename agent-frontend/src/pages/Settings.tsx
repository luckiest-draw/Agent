import { useState, useEffect } from 'react'
import { useAppStore } from '@/store/appStore'
import { api } from '@/api/client'
import { Save, Key, Plus, Trash2 } from 'lucide-react'

interface Tenant {
  id: number
  name: string
  description: string
  enabled: boolean
  apiKey: string
}

const MODELS = [
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
  { value: 'deepseek-chat', label: 'DeepSeek Chat' },
  { value: 'deepseek-reasoner', label: 'DeepSeek Reasoner' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
  { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
  { value: 'qwen-max', label: 'Qwen Max' },
  { value: 'qwen-plus', label: 'Qwen Plus' },
  { value: 'glm-4', label: 'GLM-4' },
]

export default function Settings() {
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const selectedModel = useAppStore((s) => s.selectedModel)
  const setSelectedModel = useAppStore((s) => s.setSelectedModel)
  const user = useAppStore((s) => s.user)
  const logout = useAppStore((s) => s.logout)

  useEffect(() => {
    api.get<Tenant[]>('/tenants').then(setTenants).catch(console.error)
  }, [])

  const handleCreate = async () => {
    if (!newName.trim()) return
    try {
      const t = await api.post<Tenant>('/tenants', { name: newName, description: newDesc, enabled: true })
      setTenants((prev) => [...prev, t])
      setNewName('')
      setNewDesc('')
    } catch (err) {
      console.error('Create tenant error:', err)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await api.del(`/tenants/${id}`)
      setTenants((prev) => prev.filter((t) => t.id !== id))
    } catch (err) {
      console.error('Delete tenant error:', err)
    }
  }

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Settings</h1>
        <p className="text-muted-foreground text-sm mt-1">Manage tenants, models, and account</p>
      </div>

      {/* Model Selection */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">Default Model</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          {MODELS.map(({ value, label }) => (
            <button
              key={value}
              onClick={() => setSelectedModel(value)}
              className={`px-3 py-2 rounded-lg text-sm border transition-colors ${
                selectedModel === value
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border bg-card hover:bg-accent'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </section>

      {/* Tenant Management */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">Tenants</h2>
        <div className="flex gap-2">
          <input
            placeholder="Tenant name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="flex-1 px-3 py-2 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
          <input
            placeholder="Description"
            value={newDesc}
            onChange={(e) => setNewDesc(e.target.value)}
            className="flex-1 px-3 py-2 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
          <button onClick={handleCreate} className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm hover:opacity-90">
            <Plus className="w-4 h-4" /> Add
          </button>
        </div>
        <div className="space-y-2">
          {tenants.map((t) => (
            <div key={t.id} className="flex items-center justify-between p-3 rounded-lg border border-border bg-card">
              <div>
                <p className="text-sm font-medium">{t.name}</p>
                <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                  <Key className="w-3 h-3" /> {t.apiKey || 'No API Key'}
                </p>
              </div>
              <button onClick={() => handleDelete(t.id)} className="p-1.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </section>

      {/* Account */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">Account</h2>
        <div className="p-4 rounded-xl border border-border bg-card">
          <p className="text-sm font-medium">{user?.username || 'Admin'}</p>
          <button onClick={logout} className="mt-3 text-sm text-destructive hover:underline">
            Sign Out
          </button>
        </div>
      </section>
    </div>
  )
}
