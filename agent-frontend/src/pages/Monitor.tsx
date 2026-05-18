import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line } from 'recharts'
import { Activity, Coins, Clock, Zap } from 'lucide-react'

const dailyUsage = [
  { date: '05/09', tokens: 45000, calls: 230 },
  { date: '05/10', tokens: 52000, calls: 310 },
  { date: '05/11', tokens: 38000, calls: 180 },
  { date: '05/12', tokens: 61000, calls: 420 },
  { date: '05/13', tokens: 48000, calls: 290 },
  { date: '05/14', tokens: 55000, calls: 350 },
  { date: '05/15', tokens: 42000, calls: 260 },
]

export default function Monitor() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Monitor</h1>
        <p className="text-muted-foreground text-sm mt-1">API usage and token consumption</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Tokens', value: '341K', icon: Coins, sub: 'Today: 42K' },
          { label: 'API Calls', value: '2,040', icon: Activity, sub: 'Avg 340/day' },
          { label: 'Avg Latency', value: '1.2s', icon: Clock, sub: 'P99: 3.5s' },
          { label: 'Active Models', value: '3', icon: Zap, sub: 'GPT-4o, DeepSeek, GLM-4' },
        ].map(({ label, value, icon: Icon, sub }) => (
          <div key={label} className="p-4 rounded-xl border border-border bg-card space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{label}</span>
              <Icon className="w-4 h-4 text-muted-foreground" />
            </div>
            <div>
              <p className="text-2xl font-bold">{value}</p>
              <p className="text-xs text-muted-foreground">{sub}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-xl border border-border bg-card p-6">
          <h2 className="font-semibold mb-4">Token Usage (7 days)</h2>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={dailyUsage}>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
              <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} />
              <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: '8px', color: '#e2e8f0' }} />
              <Bar dataKey="tokens" fill="#3b82f6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="rounded-xl border border-border bg-card p-6">
          <h2 className="font-semibold mb-4">API Calls (7 days)</h2>
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={dailyUsage}>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
              <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
              <YAxis stroke="#94a3b8" fontSize={12} />
              <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: '8px', color: '#e2e8f0' }} />
              <Line type="monotone" dataKey="calls" stroke="#22c55e" strokeWidth={2} dot={{ fill: '#22c55e' }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  )
}
