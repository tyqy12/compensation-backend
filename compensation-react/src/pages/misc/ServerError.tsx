import React from 'react';
import { Result, Button, Space, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { HomeOutlined, ReloadOutlined, CustomerServiceOutlined } from '@ant-design/icons';

const { Text } = Typography;

const ServerError: React.FC = () => {
  const navigate = useNavigate();

  const handleGoBack = () => {
    navigate(-1);
  };

  const handleReload = () => {
    window.location.reload();
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        backgroundColor: '#f5f5f5',
        padding: '24px',
      }}
    >
      <div style={{ textAlign: 'center', maxWidth: 600 }}>
        <Result
          status="500"
          title="500 - 服务器错误"
          subTitle="抱歉，服务器出现了一些问题，暂时无法处理您的请求。我们的技术团队已经收到通知，正在紧急修复中。"
          extra={
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Space wrap size="middle" style={{ justifyContent: 'center' }}>
                <Button type="primary" icon={<HomeOutlined />}>
                  <Link to="/">返回首页</Link>
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleReload}>
                  重新加载
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleGoBack}>
                  返回上页
                </Button>
                <Button icon={<CustomerServiceOutlined />}>联系客服</Button>
              </Space>
              <Text style={{ fontSize: 14, color: '#666', display: 'block', textAlign: 'center' }}>
                您可以稍后再试，或者联系我们的技术支持团队
              </Text>
            </Space>
          }
        />
      </div>
    </div>
  );
};

export default ServerError;
