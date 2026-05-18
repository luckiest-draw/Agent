// Zustand Store: Global application state
import { create } from 'zustand';

interface User {
  id: number;
  username: string;
  roles: string[];
}

interface AppState {
  // Auth
  user: User | null;
  token: string | null;
  setAuth: (user: User, token: string) => void;
  logout: () => void;

  // UI
  sidebarOpen: boolean;
  toggleSidebar: () => void;

  // Tenant
  currentTenantId: number | null;
  setCurrentTenant: (id: number) => void;

  // Model
  selectedModel: string;
  setSelectedModel: (model: string) => void;
}

export const useAppStore = create<AppState>((set) => ({
  user: null,
  token: localStorage.getItem('token'),
  setAuth: (user, token) => {
    localStorage.setItem('token', token);
    set({ user, token });
  },
  logout: () => {
    localStorage.removeItem('token');
    set({ user: null, token: null });
  },

  sidebarOpen: true,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),

  currentTenantId: null,
  setCurrentTenant: (id) => set({ currentTenantId: id }),

  selectedModel: 'deepseek-v4-pro',
  setSelectedModel: (model) => set({ selectedModel: model }),
}));
