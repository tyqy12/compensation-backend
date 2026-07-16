/**
 * StatusTag 组件
 *
 * 连接状态标签组件，根据连接状态显示不同的图标和颜色
 * 纯展示组件，无业务逻辑
 */

import React from 'react';
import { Tag } from 'antd';
import {
  CheckCircleOutlined,
  DisconnectOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { CONNECTION_STATUS_MAP } from '../constants';

interface StatusTagProps {
  status: string;
}

const StatusTag: React.FC<StatusTagProps> = ({ status }) => {
  const getStatusDisplay = () => {
    switch (status) {
      case 'connected':
        return {
          icon: <CheckCircleOutlined />,
          text: CONNECTION_STATUS_MAP.connected.text,
          color: CONNECTION_STATUS_MAP.connected.color,
        };
      case 'disconnected':
        return {
          icon: <DisconnectOutlined />,
          text: CONNECTION_STATUS_MAP.disconnected.text,
          color: CONNECTION_STATUS_MAP.disconnected.color,
        };
      default:
        return {
          icon: <ExclamationCircleOutlined />,
          text: CONNECTION_STATUS_MAP.unknown.text,
          color: CONNECTION_STATUS_MAP.unknown.color,
        };
    }
  };

  const display = getStatusDisplay();

  return (
    <Tag icon={display.icon} color={display.color} className="integration-status-tag" size="small">
      {display.text}
    </Tag>
  );
};

export default StatusTag;
