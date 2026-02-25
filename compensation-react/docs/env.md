# Environment Variables (Vite)

Create `.env` or `.env.local`:

```
VITE_API_BASE_URL=http://localhost:8080/api
```

Usage:

```
export const API_BASE = import.meta.env.VITE_API_BASE_URL as string;
```

Optional:

- `VITE_APP_NAME="Compensation Admin"`
- `VITE_ENABLE_MOCK=false`
