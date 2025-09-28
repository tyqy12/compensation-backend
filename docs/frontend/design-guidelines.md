# Design Guidelines

## Naming & Files
- Components: PascalCase, hooks: useCamelCase, files kebab-case or PascalCase for components
- One component per file; collocate styles and tests

## Forms
- Align labels left; use width="md" for typical inputs
- Show required asterisk; validate onBlur + onSubmit
- For secrets/keys, mask in UI and provide "reveal" with caution

## Tables
- Default page size 10/20; keep columns <= 8 without horizontal scroll
- Provide quick filters in ProTable `toolbar` when needed

## Empty/Loading/Error
- Use shared components for consistency
- Avoid abrupt layout shifts; skeletons for heavy sections

## Internationalization
- Keep copy centralized for later i18n (e.g., en-US/zh-CN), even if phase‑1 is Chinese only

## Accessibility
- Keyboard navigable forms; proper aria labels; color contrast considerate

## Performance
- Code‑split routes; `staleTime` for lists to reduce refetching
- Memoize heavy components; avoid prop‑drilling (use context/store)
