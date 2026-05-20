// API Client: HTTP requests to Java backend
const BASE_URL = '/api';

let authToken: string | null = localStorage.getItem('token');

export function setToken(token: string | null) {
  authToken = token;
  if (token) localStorage.setItem('token', token);
  else localStorage.removeItem('token');
}

function headers(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (authToken) h['Authorization'] = `Bearer ${authToken}`;
  return h;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: headers(),
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || res.statusText);
  }
  const json = await res.json();
  return json.data as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),

  // Auth
  login: (username: string, password: string) =>
    api.post<{ token: string; user: unknown }>('/auth/login', { username, password }),

  // Chat
  chatStream: (query: string, history: unknown[], model?: string) =>
    fetch(`${BASE_URL}/conversations/stream`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify({ query, history, model }),
    }),

  // Image upload (multipart)
  uploadImage: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const h: Record<string, string> = {};
    if (authToken) h['Authorization'] = `Bearer ${authToken}`;
    return fetch(`${BASE_URL}/conversations/upload-image`, {
      method: 'POST',
      headers: h,
      body: formData,
    }).then(res => res.json());
  },

  // Audio transcription (multipart)
  transcribeAudio: (audioBlob: Blob) => {
    const formData = new FormData();
    formData.append('file', audioBlob, 'recording.webm');
    const h: Record<string, string> = {};
    if (authToken) h['Authorization'] = `Bearer ${authToken}`;
    return fetch(`${BASE_URL}/conversations/transcribe`, {
      method: 'POST',
      headers: h,
      body: formData,
    }).then(res => res.json());
  },

  // Knowledge
  uploadDocument: (file: File, tenantId: number) => {
    const formData = new FormData();
    formData.append('file', file);
    return fetch(`${BASE_URL}/knowledge/documents/upload`, {
      method: 'POST',
      headers: { Authorization: headers().Authorization, 'X-Tenant-Id': String(tenantId) },
      body: formData,
    });
  },
};
