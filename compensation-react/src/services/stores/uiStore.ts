import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark';

type UIState = {
  theme: ThemeMode;
  collapsed: boolean;
  setTheme: (t: ThemeMode) => void;
  toggleTheme: () => void;
  toggleCollapsed: () => void;
};

const readTheme = (): ThemeMode => {
  const t = localStorage.getItem('theme');
  return (t === 'dark' || t === 'light') ? t : 'light';
};

export const useUIStore = create<UIState>((set, get) => ({
  theme: readTheme(),
  collapsed: false,
  setTheme: (t) => {
    try { localStorage.setItem('theme', t); } catch {}
    set({ theme: t });
  },
  toggleTheme: () => {
    const next = get().theme === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('theme', next); } catch {}
    set({ theme: next });
  },
  toggleCollapsed: () => set((s) => ({ collapsed: !s.collapsed })),
}));

