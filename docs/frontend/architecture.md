# Architecture & Structure

## Folder Structure
```
src/
  app/
    App.tsx              # App root
    AppProviders.tsx     # Antd/Redux/Query/Router providers
    theme.ts             # Antd tokens + theme config
  routes/
    index.tsx            # Route objects
    ProtectedRoute.tsx   # RBAC guard
  pages/
    auth/
      Login.tsx
      OAuthCallback.tsx
    dashboard/
      Dashboard.tsx
    system/
      IntegrationConfig.tsx
      OrgSync.tsx
    admin/
      UserBinding.tsx
    employees/
      List.tsx
      Detail.tsx
    payments/
      Batches.tsx
      BatchDetail.tsx
  components/
    Layout/
      AppLayout.tsx      # ProLayout or custom layout
    Common/
      PageHeader.tsx
      EmptyState.tsx
      Loading.tsx
  services/
    api.ts               # axios instance + interceptors
    queries/
      integration.ts     # useIntegrationQuery/useSaveIntegrationMutation
      auth.ts            # useLogin, useRefresh, useLogout
      org.ts             # usePlatforms, useSync, useCheck
    stores/
      authSlice.ts       # Redux Toolkit slice for session & roles
      uiStore.ts         # Zustand (optional) for lightweight UI
  hooks/
    useAuthGuard.ts
    useTitle.ts
  utils/
    form.ts, rbac.ts, error.ts
  types/
    api.ts               # ApiResponse<T>, DTOs
  styles/
    global.less
  config/
    env.ts               # VITE_API_BASE_URL
```

## Providers Order
- ConfigProvider (Antd theme)
- Redux Provider
- QueryClientProvider
- RouterProvider

## Data Flow
- Server State → TanStack Query: lists/detail/submit mutations
- Client State → Redux Toolkit: auth/session, role list, global UI preferences
- Feature UI State → Zustand (optional): local toggles, transient filters

## Error Handling
- A standardized axios error transformer (services/api.ts)
- Global Antd message/notification for critical failures
- Page‑level empty/error states (components/Common)
