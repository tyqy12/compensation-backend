// Lightweight loader + register helpers for WeCom JS-SDK (ww)
// Works without npm dep by injecting CDN script when package is absent
import api from '@services/api';

type Signature = { timestamp: number; nonceStr: string; signature: string };

declare global {
  interface Window {
    ww?: any;
  }
}

export function isWeComEnv(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  return /wxwork|wecom/i.test(ua) || (window as any).__wxjs_environment === 'wxwork';
}

export async function ensureWeComSDK(): Promise<any> {
  if (window.ww) return window.ww;
  // Try CDN injection
  await new Promise<void>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://wwcdn.weixin.qq.com/node/open/js/wecom-jssdk-2.3.1.js';
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('加载企业微信 JSSDK 失败'));
    document.head.appendChild(s);
  });
  return window.ww;
}

export function currentPageUrl(): string {
  const { origin, pathname, search } = window.location;
  return origin + pathname + search;
}

export async function fetchCorpSignature(url: string): Promise<Signature> {
  const { data } = await api.get('/auth/wecom/jsapi-signature', { params: { url } });
  // Expect unwrap shape: { data: { timestamp, nonceStr, signature } }
  return (data.data ?? data) as Signature;
}

export async function fetchAgentSignature(url: string): Promise<Signature> {
  const { data } = await api.get('/auth/wecom/agent-jsapi-signature', { params: { url } });
  return (data.data ?? data) as Signature;
}

export type RegisterOptions = {
  corpId: string;
  agentId?: number;
  jsApiList?: string[];
};

export async function registerWeCom(opts: RegisterOptions): Promise<void> {
  const ww = await ensureWeComSDK();
  const url = currentPageUrl();
  const corpSig = await fetchCorpSignature(url);
  const agentId = opts.agentId;

  const payload: any = {
    corpId: opts.corpId,
    jsApiList: opts.jsApiList ?? [],
    async getConfigSignature() {
      return corpSig;
    },
  };

  if (agentId) {
    const agentSig = await fetchAgentSignature(url);
    payload.agentId = agentId;
    payload.getAgentConfigSignature = async () => agentSig;
  }

  await ww.register(payload);
}
