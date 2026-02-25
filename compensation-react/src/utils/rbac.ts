export function normalizeRoles(roles: string[]): string[] {
  return roles.map((r) => r.replace(/^ROLE_/i, ''));
}

export function hasAnyRole(userRoles: string[], required: string[]): boolean {
  const ur = normalizeRoles(userRoles);
  const rr = normalizeRoles(required);
  return rr.some((r) => ur.includes(r));
}
