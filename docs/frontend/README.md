# Frontend Blueprint (React + Vite + Ant Design + ProComponents)

This folder defines the agreed stack, structure, conventions, and a practical checklist to bootstrap the web admin for the Compensation Assistant System.

## Tech Stack
- Build: Vite + TypeScript
- UI: Ant Design 5 + @ant-design/pro-components
- Router: React Router v6
- Server State: TanStack Query v5 (@tanstack/react-query)
- HTTP: axios
- Client State: Redux Toolkit (global/auth) + optional Zustand (feature‑scoped ephemeral state)
- Testing: Vitest + React Testing Library (e2e optional: Playwright)
- Lint/Format: ESLint + Prettier + Stylelint (optional)

See details in stack.md.

## Quick Start (scaffold outline)
1) Create project
- npm create vite@latest compensation-frontend -- --template react-ts

2) Install deps
- npm i antd @ant-design/pro-components @ant-design/icons
- npm i @tanstack/react-query axios
- npm i @reduxjs/toolkit react-redux zustand
- npm i react-router-dom
- npm i dayjs clsx
- npm i -D eslint prettier @typescript-eslint/eslint-plugin @typescript-eslint/parser eslint-config-prettier eslint-plugin-react eslint-plugin-react-hooks
- npm i -D vitest jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom

3) Wire providers (see architecture.md: AppProviders)
- Antd ConfigProvider + theme tokens
- QueryClientProvider
- Redux Provider
- RouterProvider

4) Configure axios instance with interceptors (see auth.md)
- Attach Authorization header
- Auto refresh on 401 once, then logout

5) Create routes/pages skeleton (see routing.md + checklist.md)

## Project Structure (TL;DR)
See architecture.md for details.

```
src/
  app/            # AppProviders, global styles, theme
  routes/         # route objects + guards (ProtectedRoute)
  pages/          # page components (feature folders)
  components/     # shared UI components
  services/       # api.ts axios, queries/* hooks, stores/* redux/zustand
  hooks/          # reusable hooks
  utils/          # helpers
  types/          # TS types (ApiResponse, DTOs)
  styles/         # global.less / tokens
  config/         # env + constants
```

## What to Build First
- Auth: Login, OAuth Redirect/Callback, GuardedLayout
- Admin: Integration Config CRUD + Test Connectivity
- System: Org Sync panel (sync all/by platform, view last results)
- Admin: User–Platform Binding (bind/unbind)

More in checklist.md.

