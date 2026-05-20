import { useState, useRef, useCallback } from 'react';
import { Mic, X, Send } from 'lucide-react';
import { api } from '@/api/client';

interface Props {
  onTranscribed: (text: string) => void;
  disabled?: boolean;
}

export default function AudioRecorder({ onTranscribed, disabled }: Props) {
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [transcribing, setTranscribing] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number>(0);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
      mediaRecorderRef.current = recorder;
      chunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      recorder.onstop = async () => {
        stream.getTracks().forEach(t => t.stop());
        if (chunksRef.current.length === 0) return;
        setTranscribing(true);
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
        try {
          const res = await api.transcribeAudio(blob);
          if (res.code === 200 && res.data?.text) {
            onTranscribed(res.data.text);
          }
        } catch (err) {
          console.error('Transcribe error:', err);
        } finally {
          setTranscribing(false);
          setRecording(false);
          setElapsed(0);
        }
      };

      recorder.start();
      setRecording(true);
      setElapsed(0);
      timerRef.current = window.setInterval(() => {
        setElapsed(prev => prev + 1);
      }, 1000);
    } catch (err) {
      console.error('Microphone error:', err);
    }
  }, [onTranscribed]);

  const stopAndSend = () => {
    clearInterval(timerRef.current);
    mediaRecorderRef.current?.stop();
  };

  const cancelRecording = () => {
    clearInterval(timerRef.current);
    if (mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.onstop = () => {};
      mediaRecorderRef.current.stream.getTracks().forEach(t => t.stop());
    }
    chunksRef.current = [];
    setRecording(false);
    setElapsed(0);
  };

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, '0')}`;
  };

  if (transcribing) {
    return (
      <div className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground">
        <div className="w-3 h-3 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        转写中...
      </div>
    );
  }

  if (recording) {
    return (
      <div className="flex items-center gap-3 px-3 py-2 bg-red-50 dark:bg-red-950/20 rounded-lg">
        <div className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse" />
        <span className="text-sm text-red-600 dark:text-red-400 font-mono">
          {formatTime(elapsed)}
        </span>
        <div className="flex-1" />
        <button
          onClick={cancelRecording}
          className="px-2 py-1 text-xs rounded hover:bg-red-100 dark:hover:bg-red-900/30"
        >
          取消
        </button>
        <button
          onClick={stopAndSend}
          className="flex items-center gap-1 px-3 py-1 text-xs rounded bg-primary text-primary-foreground hover:opacity-90"
        >
          <Send className="w-3 h-3" />
          发送
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={startRecording}
      disabled={disabled}
      className="p-2 rounded-lg hover:bg-accent disabled:opacity-50 transition-colors"
      title="录音"
    >
      <Mic className="w-5 h-5 text-muted-foreground" />
    </button>
  );
}
