# Routing & RBAC Guards

## Public vs Protected
- Public routes: /login, /oauth/callback/:platform
- Protected routes: everything else

## Guard Strategy
- ProtectedRoute checks `isAuthenticated` from auth slice
- Optional route meta `roles: ['ADMIN']` checked in guard (has any role)
- On 401 from API, interceptor triggers refresh; if fails, redirect to login with `from` param

## Example Route Object
```
{
  path: '/system/integration',
  element: <ProtectedRoute roles={['ADMIN']}><IntegrationConfigPage/></ProtectedRoute>
}
```

