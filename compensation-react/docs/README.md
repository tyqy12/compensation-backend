# Frontend Blueprint & Docs Index

This folder contains the blueprint, architecture, API contracts, and domain specs for the Compensation Admin frontend. Use the index below to navigate.

## Documentation Index

- Getting Started
  - Stack overview: [stack.md](stack.md)
  - Environment: [env.md](env.md)
  - Project blueprint (this page): [README.md](README.md)
- Architecture & Patterns
  - App structure: [architecture.md](architecture.md)
  - Routing & guards: [routing.md](routing.md)
  - State management: [state.md](state.md)
  - API client & contracts: [api.md](api.md)
- UI & Guidelines
  - UI components & ProComponents: [ui.md](ui.md)
  - Design guidelines: [design-guidelines.md](design-guidelines.md)
- Testing & Quality
  - Testing strategy: [testing.md](testing.md)
  - Implementation checklist: [checklist.md](checklist.md)
- Business APIs
  - Employee API: [employee-api.md](employee-api.md)
  - Admin API: [admin-api.md](admin-api.md)
  - Dashboard API: [dashboard-api.md](dashboard-api.md)
  - Payment Batch API: [payment-batch-api.md](payment-batch-api.md)
  - Integration Config (frontend): [frontend-integration-config-api.md](frontend-integration-config-api.md)
- Domain Specs & Flows
  - Org structure (per platform): [org-structure.md](org-structure.md)
  - Employee–User–Platform linking: [employee-user-linking.md](employee-user-linking.md)
  - Platform binding conflict approval: [platform-binding-approval.md](platform-binding-approval.md)
- Plans & Roadmap
  - Dynamic permissions plan: [frontend-dynamic-permissions-plan.md](frontend-dynamic-permissions-plan.md)
  - Project issues & plan: [plan-issues.md](plan-issues.md)

## Tech Stack

- Build: Vite + TypeScript
- UI: Ant Design 5 + @ant-design/pro-components
- Router: React Router v6
- Server State: TanStack Query v5 (@tanstack/react-query)
- HTTP: axios
- Client State: Redux Toolkit (global/auth) + optional Zustand (feature‑scoped ephemeral state)
- Testing: Vitest + React Testing Library (e2e optional: Playwright)
- Lint/Format: ESLint + Prettier + Stylelint (optional)

See details in [stack.md](stack.md).

## Quick Start (scaffold outline)

1. Create project

- npm create vite@latest compensation-frontend -- --template react-ts

2. Install deps

- npm i antd @ant-design/pro-components @ant-design/icons
- npm i @tanstack/react-query axios
- npm i @reduxjs/toolkit react-redux zustand
- npm i react-router-dom
- npm i dayjs clsx
- npm i -D eslint prettier @typescript-eslint/eslint-plugin @typescript-eslint/parser eslint-config-prettier eslint-plugin-react eslint-plugin-react-hooks
- npm i -D vitest jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom

3. Wire providers (see [architecture.md](architecture.md): AppProviders)

- Antd ConfigProvider + theme tokens
- QueryClientProvider
- Redux Provider
- RouterProvider

4. Configure axios instance with interceptors (see [auth.md](auth.md))

- Attach Authorization header
- Auto refresh on 401 once, then logout

5. Create routes/pages skeleton (see [routing.md](routing.md) + [checklist.md](checklist.md))

## Project Structure (TL;DR)

See [architecture.md](architecture.md) for details.

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

More in [checklist.md](checklist.md).
