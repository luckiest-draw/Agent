import { useState, useCallback } from 'react'
import ReactFlow, {
  Node, Edge, Controls, Background, addEdge,
  Connection, useNodesState, useEdgesState, MiniMap,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { Plus, Save, Play } from 'lucide-react'
import { useAppStore } from '@/store/appStore'

const NODE_TYPES = [
  { type: 'start', label: 'Start', color: '#22c55e' },
  { type: 'agent', label: 'Agent', color: '#3b82f6' },
  { type: 'tool', label: 'Tool', color: '#f59e0b' },
  { type: 'condition', label: 'Condition', color: '#a855f7' },
  { type: 'end', label: 'End', color: '#ef4444' },
]

export default function Workflow() {
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [name, setName] = useState('New Workflow')
  const selectedModel = useAppStore((s) => s.selectedModel)

  const onConnect = useCallback(
    (connection: Connection) => setEdges((eds) => addEdge(connection, eds)),
    [setEdges]
  )

  const addNode = (type: string, label: string) => {
    const id = `${type}-${Date.now()}`
    const newNode: Node = {
      id,
      type: 'default',
      data: { label },
      position: { x: Math.random() * 300 + 50, y: Math.random() * 200 + 50 },
      style: { background: '#1e293b', border: '1px solid #334155', color: '#e2e8f0', borderRadius: '8px', padding: '10px 16px' },
    }
    setNodes((nds) => [...nds, newNode])
  }

  const handleSave = async () => {
    const token = useAppStore.getState().token
    try {
      const res = await fetch('/api/workflows', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ name, description: '', nodes, edges }),
      })
      if (res.ok) alert('Workflow saved!')
    } catch (err) {
      console.error('Save error:', err)
    }
  }

  return (
    <div className="h-[calc(100vh-7rem)] flex flex-col space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="text-2xl font-bold bg-transparent border-b border-transparent hover:border-border focus:border-primary focus:outline-none"
          />
          <span className="text-sm text-muted-foreground">Model: {selectedModel}</span>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={handleSave} className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-card border border-border text-sm hover:bg-accent">
            <Save className="w-4 h-4" /> Save
          </button>
          <button className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-primary text-primary-foreground text-sm hover:opacity-90">
            <Play className="w-4 h-4" /> Run
          </button>
        </div>
      </div>

      <div className="flex gap-2 flex-wrap">
        {NODE_TYPES.map(({ type, label, color }) => (
          <button
            key={type}
            onClick={() => addNode(type, label)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border bg-card text-xs hover:bg-accent transition-colors"
          >
            <span className="w-2 h-2 rounded-full" style={{ background: color }} />
            {label}
            <Plus className="w-3 h-3" />
          </button>
        ))}
      </div>

      <div className="flex-1 rounded-xl border border-border bg-card overflow-hidden">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
        >
          <Background color="#334155" gap={20} />
          <Controls className="bg-card fill-foreground" />
          <MiniMap style={{ background: '#1e293b' }} maskColor="rgba(0,0,0,0.5)" />
        </ReactFlow>
      </div>
    </div>
  )
}
