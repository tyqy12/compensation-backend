import React from 'react';
import { Result, Button, Space, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { HomeOutlined, LoginOutlined, ReloadOutlined } from '@ant-design/icons';

const { Text } = Typography;

const Forbidden: React.FC = () => {
  const navigate = useNavigate();

  const handleGoBack = () => {
    navigate(-1);
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
          status="403"
          title="403 - 访问被拒绝"
          subTitle="抱歉，您暂时没有权限访问此页面。这可能是由于权限不足或会话已过期导致的。"
          extra={
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Space wrap size="middle" style={{ justifyContent: 'center' }}>
                <Button type="primary" icon={<HomeOutlined />}>
                  <Link to="/">返回首页</Link>
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleGoBack}>
                  返回上页
                </Button>
                <Button icon={<LoginOutlined />}>
                  <Link to="/login">重新登录</Link>
                </Button>
              </Space>
              <Text style={{ fontSize: 14, color: '#666', display: 'block', textAlign: 'center' }}>
                如果您认为这是一个错误，请联系系统管理员
              </Text>
            </Space>
          }
        />
      </div>
    </div>
  );
};

export default Forbidden;
