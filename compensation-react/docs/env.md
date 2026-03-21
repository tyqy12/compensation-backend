# Environment Variables (Vite)

Create `.env` or `.env.local`:

```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_OAUTH_REDIRECT_URI_WECHAT=https://admin.example.com/oauth/callback/wechat
```

Usage:

```
export const API_BASE = import.meta.env.VITE_API_BASE_URL as string;
```

Optional:

- `VITE_APP_NAME="Compensation Admin"`
- `VITE_ENABLE_MOCK=false`
- `VITE_OAUTH_REDIRECT_URI_WECHAT`：企业微信 OAuth 回调完整 URL。配置后会优先使用该地址，避免 `redirect_uri` 与企业微信后台配置域名不一致。
