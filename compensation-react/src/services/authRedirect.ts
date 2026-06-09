import type { Location } from 'react-router-dom';

function toPath(value?: Partial<Location> | null) {
  if (!value?.pathname || value.pathname === '/login') {
    return undefined;
  }
  return `${value.pathname}${value.search ?? ''}${value.hash ?? ''}`;
}

function readStoredRedirect() {
  try {
    const redirect = sessionStorage.getItem('auth_redirect');
    if (!redirect || redirect === '/login' || redirect.startsWith('/login?')) {
      return undefined;
    }
    return redirect;
  } catch {
    return undefined;
  }
}

export function consumePostLoginRedirect(locationState?: unknown) {
  const stateRedirect = toPath((locationState as { from?: Partial<Location> } | null)?.from);
  const storedRedirect = readStoredRedirect();
  try {
    sessionStorage.removeItem('auth_redirect');
  } catch {}
  return stateRedirect ?? storedRedirect ?? '/';
}
