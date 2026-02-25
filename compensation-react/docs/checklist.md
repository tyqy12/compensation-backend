# Implementation Checklist

## Setup

- [ ] Scaffold Vite React TS project
- [ ] Install dependencies (see README.md Quick Start)
- [ ] Configure ESLint + Prettier
- [ ] Add AppProviders and global theme

## Auth

- [ ] Build axios instance with interceptors
- [ ] Implement auth slice (Redux) with persistence
- [ ] Login page (username/password)
- [ ] OAuth authorize redirect + callback page
- [ ] ProtectedRoute guard + role meta
- [ ] Refresh & logout flows

## Admin: Integration Config

- [ ] List platforms + read config
- [ ] Edit/Save config form (masked read, write validated)
- [ ] Test Connection action + toast result

## System: Org Sync

- [ ] Platforms list & statuses
- [ ] Sync all / Sync by platform buttons
- [ ] Show last results / errors summary

## Admin: User Binding

- [ ] Search users
- [ ] Bind/unbind platform account
- [ ] Conflict handling (409)

## Shared

- [ ] ProTable + ProForm patterns
- [ ] Error/empty/loading components
- [ ] Query hooks (TanStack Query) per feature
- [ ] RBAC utilities and menu filtering

## Testing & CI

- [ ] Vitest + RTL setup and first tests
- [ ] (Optional) Playwright E2E smoke
