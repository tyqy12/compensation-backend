# UI & ProComponents Guidelines

## Theme & Tokens

- Centralize tokens in `app/theme.ts` (AntD 5 Design Token)
- Primary color, success/warn/error colors aligned with brand
- Enable dark mode later via `algorithm: theme.darkAlgorithm`

## Layout

- Prefer ProLayout from `@ant-design/pro-components` for admin shell; or AntD Layout if simpler
- PageHeader: common title/extra/actions

## Tables

- Use ProTable for lists with built‑in search form, pagination, column state
- Server‑side pagination via TanStack Query (pass params into query key)
- Default features: column pinning, density, export (if needed)

## Forms

- Use ProForm family (ProForm, ModalForm, StepsForm)
- Validation with AntD rules; async validation for unique keys when necessary
- For configuration forms, show a "Test Connection" button

## Feedback

- message for transient success/errors
- notification for long‑running operations or background tasks

## Accessibility

- Use semantic elements where possible, label inputs, keyboard focus visible
