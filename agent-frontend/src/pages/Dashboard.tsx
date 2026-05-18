import { Bot, MessageSquare, BookOpen, GitBranch, BarChart3, TrendingUp, Layers } from 'lucide-react'

const stats = [
  { label: 'Conversations', value: '1,234', icon: MessageSquare, trend: '+12%' },
  { label: 'Documents', value: '456', icon: BookOpen, trend: '+8%' },
  { label: 'Workflows', value: '23', icon: GitBranch, trend: '+5%' },
  { label: 'API Calls', value: '45.2K', icon: BarChart3, trend: '+18%' },
]

export default function Dashboard() {
  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground mt-1">Overview of your AI Agent platform</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map(({ label, value, icon: Icon, trend }) => (
          <div key={label} className="p-4 rounded-xl border border-border bg-card space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{label}</span>
              <Icon className="w-4 h-4 text-muted-foreground" />
            </div>
            <div className="flex items-end justify-between">
              <span className="text-2xl font-bold">{value}</span>
              <span className="text-xs text-green-500 font-medium">{trend}</span>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-xl border border-border bg-card p-6">
          <h2 className="font-semibold mb-4 flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-primary" />
            Recent Activity
          </h2>
          <div className="space-y-3">
            {[
              { action: 'Document uploaded', detail: 'product-manual.pdf', time: '2 min ago' },
              { action: 'Chat completed', detail: 'Customer support query', time: '15 min ago' },
              { action: 'Workflow executed', detail: 'Order processing', time: '1 hour ago' },
              { action: 'Knowledge indexed', detail: 'FAQ dataset (200 chunks)', time: '3 hours ago' },
            ].map((item, i) => (
              <div key={i} className="flex items-center justify-between py-2 border-b border-border last:border-0">
                <div>
                  <p className="text-sm font-medium">{item.action}</p>
                  <p className="text-xs text-muted-foreground">{item.detail}</p>
                </div>
                <span className="text-xs text-muted-foreground">{item.time}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-6">
          <h2 className="font-semibold mb-4 flex items-center gap-2">
            <Layers className="w-4 h-4 text-primary" />
            System Status
          </h2>
          <div className="space-y-4">
            {[
              { name: 'PostgreSQL + Pgvector', status: 'Running', color: 'bg-green-500' },
              { name: 'Redis', status: 'Running', color: 'bg-green-500' },
              { name: 'RabbitMQ', status: 'Running', color: 'bg-green-500' },
              { name: 'AI Engine (Python)', status: 'Running', color: 'bg-green-500' },
              { name: 'Celery Workers', status: 'Active (3/3)', color: 'bg-green-500' },
            ].map((svc) => (
              <div key={svc.name} className="flex items-center justify-between">
                <span className="text-sm">{svc.name}</span>
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${svc.color}`} />
                  <span className="text-xs text-muted-foreground">{svc.status}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
