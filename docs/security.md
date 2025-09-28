# Security Guide (JWT + RBAC + Authorities)

This backend uses stateless JWT with Spring Security method security. Tokens carry both roles and fine‑grained authorities to protect endpoints.

## JWT Structure
- Header: `alg=HS256`
- Claims (minimum):
  - `sub`: username
  - `authorities`: comma‑separated list (e.g., `ROLE_ADMIN,approval:start,approval:read`)
  - `iat`, `exp`

Token creation and parsing:
- Build + claims: src/main/java/com/yiyundao/compensation/security/JwtTokenProvider.java:32
- Authorities persisted as a single string claim: src/main/java/com/yiyundao/compensation/security/JwtTokenProvider.java:42
- Validation/parsing: src/main/java/com/yiyundao/compensation/security/JwtTokenProvider.java:81
- Filter extracts `authorities` → `SimpleGrantedAuthority`: src/main/java/com/yiyundao/compensation/security/JwtAuthenticationFilter.java:35

## Roles vs Authorities
- Roles should be emitted as `ROLE_*` authorities inside `authorities` claim.
  - Example: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_APPROVER`
  - `hasRole('ADMIN')` checks for authority `ROLE_ADMIN`.
- Fine‑grained authorities are free‑form strings, e.g. `approval:start`, `approval:read`.
  - Checked with `hasAuthority('approval:start')`.

Typical `authorities` value for an approver manager:
```
ROLE_MANAGER,ROLE_APPROVER,approval:start,approval:approve,approval:reject,approval:read
```

## Security Configuration
- Path rules: src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java:35
  - Public: `/auth/**` (except `/auth/logout`), `/alipay/notify`, `/actuator/health`
  - Authenticated: `/auth/logout`
  - Admin only: `/system/integration/**`, `/admin/**`
  - Manager or Admin: `/manager/**`
  - All others: authenticated
- Important: Server context path is `/api`; matchers are defined on the servlet path (no `/api` prefix). See comment: src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java:33

## Method Security on Controllers
Approval endpoints (examples): src/main/java/com/yiyundao/compensation/interfaces/controller/approval/ApprovalController.java:1
- Start: `hasAnyRole('ADMIN','MANAGER') or hasAuthority('approval:start')`
- Approve/Reject: `hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:approve|reject')`
- Cancel: `hasAnyRole('ADMIN','MANAGER') or hasAuthority('approval:cancel')`
- Read (pending/detail/steps): `hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:read')`

Other examples:
- Employee sensitive decrypt APIs guarded by role: src/main/java/com/yiyundao/compensation/interfaces/controller/employee/EmployeeController.java:114

## Configuration
JWT keys and TTL are configured in profile YAMLs. Dev example: src/main/resources/application-dev.yml:39
```
jwt:
  secret: compensation-assistant-dev-secret-key-2024
  expiration: 86400000           # 1 day
  refresh-expiration: 604800000  # 7 days
```

## Generating Tokens
If you authenticate users programmatically, build the `Authentication` with authorities and call provider:
- Generate access token: src/main/java/com/yiyundao/compensation/security/JwtTokenProvider.java:32
- Generate refresh token: src/main/java/com/yiyundao/compensation/security/JwtTokenProvider.java:49

Pseudo‑code:
```
List<GrantedAuthority> auths = List.of(
  new SimpleGrantedAuthority("ROLE_MANAGER"),
  new SimpleGrantedAuthority("ROLE_APPROVER"),
  new SimpleGrantedAuthority("approval:start"),
  new SimpleGrantedAuthority("approval:approve"),
  new SimpleGrantedAuthority("approval:reject"),
  new SimpleGrantedAuthority("approval:read")
);
Authentication auth = new UsernamePasswordAuthenticationToken("alice", null, auths);
String jwt = jwtTokenProvider.generateToken(auth);
```

## Calling APIs
Send the token via `Authorization: Bearer <jwt>`:
```
curl -H "Authorization: Bearer $JWT" \
     http://localhost:8080/api/approval/workflows/pending?approverId=1
```

## Recommended Authorities
- Roles: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_APPROVER`
- Permissions: `approval:start`, `approval:approve`, `approval:reject`, `approval:cancel`, `approval:read`

Keep roles coarse‑grained and permissions fine‑grained to simplify policy management.
## Additional Protections
- OAuth `state` validation (stored in Redis, TTL 5 min) prevents CSRF/replay during OAuth logins.
- Login rate limiting & brute force protection (Redis): per‑user and per‑IP counters + temporary locks.
- Access token blacklist on logout; refresh token whitelist + rotation on refresh.
- Integration configs are stored encrypted in DB; controller returns masked values and is admin‑only.
