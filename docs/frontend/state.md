# State Management

## Responsibilities
- TanStack Query: server state (lists, detail, mutations)
- Redux Toolkit: auth/session (tokens, username, roles), global UI prefs
- Zustand (optional): local feature UI state (toggles, transient filters)

## Auth Slice (Redux Toolkit)
```
interface AuthState {
  accessToken?: string;
  refreshToken?: string;
  username?: string;
  roles: string[];
}
```
- actions: setSession, clearSession
- selectors: isAuthenticated, hasRole('ADMIN')
- persistence: mirror to localStorage on change

## Query Defaults
- retry: 1 (be conservative for admin)
- staleTime: 30_000ms (lists) / 0 (detail) as needed
- cacheTime: default
- error handling: transform axios errors → message, show AntD message

