/**
 * IntegrationConfig 模块类型定义
 *
 * 遵循项目类型定义规范，与 src/types/api.ts 保持一致
 */

import type { Platform, IntegrationConfigListItem } from '../../../types/api';

/**
 * 平台分组定义
 */
export interface PlatformGroup {
  key: string;
  name: string;
  icon: React.ReactNode;
  platforms: Platform[];
}

/**
 * 平台卡片组件属性
 */
export interface PlatformCardProps {
  item: IntegrationConfigListItem;
  onConfig: (platform: Platform) => void;
  onTest: (platform: Platform) => void;
  onDisable: (platform: Platform) => void;
  onEnable: (platform: Platform) => void;
  loading?: boolean;
}

/**
 * 平台分组组件属性
 */
export interface PlatformGroupProps {
  group: PlatformGroup;
  items: IntegrationConfigListItem[];
  onConfig: (platform: Platform) => void;
  onTest: (platform: Platform) => void;
  onDisable: (platform: Platform) => void;
  onEnable: (platform: Platform) => void;
  testLoading?: boolean;
}

/**
 * 状态标签组件属性
 */
export interface StatusTagProps {
  status: string;
}

/**
 * 配置抽屉组件属性
 */
export interface ConfigDrawerProps {
  platform: Platform | null;
  open: boolean;
  onClose: () => void;
}

/**
 * 证书上传组件属性
 */
export interface CertUploadProps {
  certType: 'appCert' | 'alipayCert' | 'alipayRootCert';
  label: string;
  value?: string;
  onChange?: (value: string) => void;
}

/**
 * 平台表单基础属性
 */
export interface PlatformFormProps {
  form: any;
}
