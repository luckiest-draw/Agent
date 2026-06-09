import { useState, useRef, useEffect } from 'react'
import { useAppStore } from '@/store/appStore'
import { Send, Bot, User } from 'lucide-react'
import ImageUploader, { type ImageInfo } from '@/components/chat/ImageUploader'
import AudioRecorder from '@/components/chat/AudioRecorder'

interface Message {
  role: 'user' | 'assistant'
  content: string
  imageUrl?: string
}

export default function Chat() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [images, setImages] = useState<ImageInfo[]>([])
  const [showImageViewer, setShowImageViewer] = useState<string | null>(null)
  const selectedModel = useAppStore((s) => s.selectedModel)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    if (streaming) return
    const text = input.trim()
    if (!text && images.length === 0) return
    setStreaming(true)

    const readyImages = images.filter(img => img.serverUrl && !img.uploading)
    const primaryImageUrl = readyImages.length > 0 ? readyImages[0].serverUrl : undefined

    const userMsg: Message = {
      role: 'user',
      content: text || '请描述这张图片',
      imageUrl: primaryImageUrl || undefined,
    }
    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setImages([])
    setStreaming(true)

    try {
      const token = useAppStore.getState().token
      // 已有会话则复用 /{id}/stream，否则新建 /stream
      const currentConvId = conversationId
      const url = currentConvId
        ? `/api/conversations/${currentConvId}/stream`
        : '/api/conversations/stream'
      const body = currentConvId
        ? JSON.stringify({
            message: text || '请描述这张图片',
            modelName: selectedModel,
            imageUrl: primaryImageUrl || undefined,
          })
        : JSON.stringify({
            query: text || '请描述这张图片',
            history: messages,
            model: selectedModel,
            imageUrl: primaryImageUrl || undefined,
          })
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body,
      })
      const reader = res.body?.getReader()
      if (!reader) return

      setMessages((prev) => [...prev, { role: 'assistant', content: '' }])

      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const jsonStr = line.slice(5).trim();
              if (!jsonStr) continue;
              const data = JSON.parse(jsonStr);
              // 收到会话 ID 后保存，后续消息复用
              if (data.conversationId) {
                setConversationId(data.conversationId)
              }
              if (data.done && data.conversationId) break
              if (data.event === 'tool_call' || data.event === 'tool_result') {
                // 工具调用仅更新状态标记，不写入消息内容
                setMessages((prev) => {
                  const updated = [...prev]
                  const last = updated[updated.length - 1]
                  if (last.role === 'assistant') {
                    const count = (last as any).toolCalls || 0
                    updated[updated.length - 1] = { ...last, toolCalls: count + 1 } as any
                  }
                  return updated
                })
              } else if (data.content) {
                setMessages((prev) => {
                  const updated = [...prev]
                  const last = updated[updated.length - 1]
                  if (last.role === 'assistant') {
                    updated[updated.length - 1] = { ...last, content: last.content + data.content }
                  }
                  return updated
                })
              }
            } catch {}
          }
        }
      }
    } catch (err) {
      console.error('Stream error:', err)
    } finally {
      setStreaming(false)
    }
  }

  const handleTranscribed = (text: string) => {
    setInput(prev => prev ? prev + ' ' + text : text)
  }

  return (
    <div className="flex flex-col h-[calc(100vh-7rem)] max-w-3xl mx-auto">
      <div className="mb-4">
        <h1 className="text-2xl font-bold">Chat</h1>
        <p className="text-muted-foreground text-sm">Model: {selectedModel}</p>
      </div>

      <div className="flex-1 overflow-auto space-y-4 pr-2">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
            <Bot className="w-12 h-12 mb-4 opacity-20" />
            <p>开始对话，可以上传图片或发送语音</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <div key={i} className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : ''}`}>
            {msg.role === 'assistant' && (
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                <Bot className="w-4 h-4 text-primary" />
              </div>
            )}
            <div className={`max-w-[80%] rounded-xl px-4 py-3 text-sm ${
              msg.role === 'user'
                ? 'bg-primary text-primary-foreground'
                : 'bg-card border border-border'
            }`}>
              {msg.imageUrl && (
                <img
                  src={msg.imageUrl}
                  alt="Attached"
                  className="max-w-[300px] max-h-[200px] rounded-lg mb-2 cursor-pointer hover:opacity-90"
                  onClick={() => setShowImageViewer(msg.imageUrl!)}
                />
              )}
              {(msg as any).toolCalls > 0 && !msg.content && (
                <p className="text-muted-foreground animate-pulse text-xs">
                  🔍 查询中...
                </p>
              )}
              {msg.content && <p className="whitespace-pre-wrap">{msg.content}</p>}
              {(msg as any).toolCalls > 0 && msg.content && (
                <p className="text-muted-foreground text-xs mt-1 opacity-50">
                  已搜索 {(msg as any).toolCalls} 次
                </p>
              )}
            </div>
            {msg.role === 'user' && (
              <div className="w-8 h-8 rounded-lg bg-accent flex items-center justify-center shrink-0">
                <User className="w-4 h-4" />
              </div>
            )}
          </div>
        ))}
        <div ref={scrollRef} />
      </div>

      <div className="mt-4 space-y-2">
        <ImageUploader images={images} onChange={setImages} />
        <div className="flex gap-2">
          <AudioRecorder onTranscribed={handleTranscribed} disabled={streaming} />
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="输入消息..."
            disabled={streaming}
            className="flex-1 px-4 py-3 rounded-xl border border-border bg-card text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-50"
          />
          <button
            onClick={handleSend}
            disabled={streaming || (!input.trim() && images.length === 0)}
            className="px-4 py-3 rounded-xl bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>

      {showImageViewer && (
        <div
          className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center cursor-pointer"
          onClick={() => setShowImageViewer(null)}
        >
          <img
            src={showImageViewer}
            alt="Viewer"
            className="max-w-[90vw] max-h-[90vh] rounded-lg object-contain"
          />
        </div>
      )}
    </div>
  )
}
