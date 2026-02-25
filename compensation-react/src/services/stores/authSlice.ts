import { configureStore, createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface User {
  id: string;
  username: string;
  roles: string[];
}

export interface AuthState {
  user: User | null;
  accessToken?: string;
  refreshToken?: string;
  username?: string;
  roles: string[];
}

const persisted = (() => {
  try {
    const raw = localStorage.getItem('auth');
    return raw ? (JSON.parse(raw) as AuthState) : undefined;
  } catch {
    return undefined;
  }
})();

const initialState: AuthState = persisted ?? { user: null, roles: [] };

const slice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // For app usage
    setSession: (state, action: PayloadAction<Partial<AuthState>>) => ({
      ...state,
      ...action.payload,
    }),
    clearSession: () => ({ user: null, roles: [] }),
    // For tests
    login: (state, action: PayloadAction<User>) => ({
      ...state,
      user: action.payload,
      roles: action.payload.roles,
    }),
    logout: () => ({ user: null, roles: [] }),
  },
});

export const { setSession, clearSession, login, logout } = slice.actions;

export const store = configureStore({
  reducer: {
    auth: slice.reducer,
  },
});

store.subscribe(() => {
  const state = store.getState().auth as AuthState;
  try {
    localStorage.setItem('auth', JSON.stringify(state));
    if (state.accessToken) {
      localStorage.setItem('auth_token', state.accessToken);
    } else if (!state.user) {
      localStorage.removeItem('auth_token');
    }
  } catch {}
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const selectAuthState = (s: RootState) => s.auth;
export const selectIsAuthenticated = (s: RootState) => Boolean(s.auth.user);
export const selectHasRole = (role: string) => (s: RootState) =>
  (s.auth.user?.roles ?? s.auth.roles).includes(role);

export default slice.reducer;
