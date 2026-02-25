import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { isWeComEnv, registerWeCom } from '@services/wecom';
import { App as AntdApp } from 'antd';

const env = (import.meta as ImportMeta).env || {};

export function useWecomRegister() {
  const { message } = AntdApp.useApp();
  const loc = useLocation();
  const isAuthed = useSelector((s: RootState) => Boolean(s.auth.user));
  useEffect(() => {
    if (!isAuthed) return;
    if (!isWeComEnv()) return;

    const enable = env.VITE_ENABLE_WECOM_JSSDK !== 'false';
    if (!enable) return;

    const corpId = env.VITE_WECOM_CORP_ID as string | undefined;
    const agentRaw = env.VITE_WECOM_AGENT_ID as string | undefined;
    const agentId = agentRaw ? Number(agentRaw) : undefined;
    if (!corpId) return;

    registerWeCom({ corpId, agentId, jsApiList: ['selectExternalContact'] }).catch((e) =>
      message.warning('企业微信环境注册失败：' + (e?.message || '未知错误')),
    );
    // Re-run on path/search changes to update signature
  }, [loc.pathname, loc.search, isAuthed, message]);
}
