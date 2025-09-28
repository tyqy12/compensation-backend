# Stack and Versions

Recommended versions (align with latest stable):
- Node: >= 18.x
- Vite: ^5
- React: ^18
- TypeScript: ^5
- Ant Design: ^5
- @ant-design/pro-components: ^2
- React Router: ^6.22
- TanStack Query: ^5
- axios: ^1.7
- Redux Toolkit: ^2.2, react-redux ^9
- Zustand: ^4.5
- Vitest: ^2, @testing-library/react ^14

Why this stack:
- AntD + ProComponents excels at forms, tables, advanced filters.
- TanStack Query manages server state lifecycles (cache, staleTime, retries, background refresh).
- Redux Toolkit holds auth/session, UI preferences, RBAC; Zustand for small feature stores.
