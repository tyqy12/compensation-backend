# Testing Strategy

## Unit/Component Tests (Vitest + RTL)
- Test critical components (forms, tables actions)
- Mock network with MSW or axios mock adapter for hooks
- Put tests alongside components: `ComponentName.test.tsx`

## E2E (optional, Playwright)
- Smoke login, protected route guard, basic CRUD flows
- CI headless run on push

## Coverage Targets
- Aim for 60–70% lines on core modules (auth, integration, org)
