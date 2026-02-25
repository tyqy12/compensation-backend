# Repository Guidelines

## Project Structure & Module Organization

- Source lives under `src/`:
  - `app/` providers, theme; `routes/` route objects/guards; `pages/<feature>/` UI pages; `components/` shared UI; `services/` (`api.ts`, `queries/*`, `stores/*`); `hooks/`; `utils/`; `types/`; `styles/`; `config/`.
- Public assets in `public/`. Collocate tests with code (`*.test.ts` / `*.test.tsx`).
- See `docs/architecture.md`, `docs/routing.md`, and `docs/state.md` for details.

## Build, Test, and Development Commands

- `npm i` — install dependencies.
- `npm run dev` — start Vite dev server.
- `npm run build` — production build to `dist/`.
- `npm run preview` — preview the built app.
- `npm run lint` — run ESLint/Prettier (and Stylelint if configured).
- `npm run test` / `npm run test:watch` — run Vitest.

## Coding Style & Naming Conventions

- TypeScript with 2‑space indentation; formatting enforced by Prettier.
- Components: PascalCase (`UserBinding.tsx`). Hooks: `useCamelCase` (`useAuthGuard.ts`).
- Files: kebab-case by default; component files/folders may use PascalCase.
- Variables/functions: camelCase; constants: UPPER_SNAKE_CASE.
- Prefer named exports; collocate component, styles, and tests.

## Testing Guidelines

- Frameworks: Vitest + React Testing Library (optional e2e: Playwright).
- Name tests `ComponentName.test.tsx`; place next to the component/hook.
- Mock network via MSW or axios-mock-adapter for query hooks.
- Coverage: aim for 60–70% on core modules (auth, integration, org).

## Commit & Pull Request Guidelines

- Use Conventional Commits: `type(scope): short message` (e.g., `feat(routes): guard protected pages`).
- Include clear PR description, linked issues (e.g., `Fixes #123`), and screenshots/GIFs for UI changes.
- PR checklist: tests added/updated, `npm run lint` passes, relevant `docs/*` updated.

## Security & Configuration Tips

- Create `.env.local` with `VITE_API_BASE_URL=http://localhost:8080/api` (see `docs/env.md`).
- Do not commit secrets. Avoid logging tokens. Auth headers/refresh are handled in `services/api.ts` interceptors.

## Architecture Notes

- Providers order: AntD ConfigProvider → Redux → QueryClient → Router.
- Server state: TanStack Query; auth/session: Redux Toolkit; optional feature state: Zustand.
