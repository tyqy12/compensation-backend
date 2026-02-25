import { useEffect } from 'react';
import { appName } from '@app/theme';

export function useTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · ${appName}` : appName;
  }, [title]);
}
