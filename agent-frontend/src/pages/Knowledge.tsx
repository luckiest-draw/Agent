import { useState, useRef } from 'react'
import { Upload, FileText, CheckCircle, XCircle, Loader2 } from 'lucide-react'
import { useAppStore } from '@/store/appStore'

interface DocRecord {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  status: string
  chunkCount: number
}

const STATUS_ICON: Record<string, JSX.Element> = {
  DONE: <CheckCircle className="w-4 h-4 text-green-500" />,
  FAILED: <XCircle className="w-4 h-4 text-destructive" />,
  PENDING: <Loader2 className="w-4 h-4 text-yellow-500 animate-spin" />,
}

const ACCEPTED_TYPES = '.pdf,.docx,.txt,.md,.html,.jpg,.jpeg,.png,.gif,.bmp'

export default function Knowledge() {
  const [docs, setDocs] = useState<DocRecord[]>([])
  const [uploading, setUploading] = useState(false)
  const tenantId = useAppStore((s) => s.currentTenantId)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !tenantId) return
    setUploading(true)
    try {
      const token = useAppStore.getState().token
      const formData = new FormData()
      formData.append('file', file)
      const res = await fetch('/api/knowledge/documents/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': String(tenantId) },
        body: formData,
      })
      if (res.ok) {
        const json = await res.json()
        setDocs((prev) => [json.data, ...prev])
      }
    } catch (err) {
      console.error('Upload error:', err)
    } finally {
      setUploading(false)
    }
  }

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Knowledge Base</h1>
          <p className="text-muted-foreground text-sm mt-1">
            Upload documents for RAG-powered AI responses
          </p>
        </div>
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading || !tenantId}
          className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 disabled:opacity-50"
        >
          {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
          Upload
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_TYPES}
          onChange={handleUpload}
          className="hidden"
        />
      </div>

      <div className="rounded-xl border border-border bg-card">
        {docs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <FileText className="w-12 h-12 mb-4 opacity-20" />
            <p className="text-sm">No documents yet</p>
            <p className="text-xs mt-1">Upload PDF, Word, Markdown, HTML, TXT or images</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {docs.map((doc) => (
              <div key={doc.id} className="flex items-center justify-between px-4 py-3">
                <div className="flex items-center gap-3">
                  <FileText className="w-5 h-5 text-muted-foreground" />
                  <div>
                    <p className="text-sm font-medium">{doc.fileName}</p>
                    <p className="text-xs text-muted-foreground">
                      {doc.fileType?.toUpperCase()} · {formatSize(doc.fileSize || 0)} · {doc.chunkCount} chunks
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-muted-foreground">{doc.status}</span>
                  {STATUS_ICON[doc.status] || null}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
