import React from 'react';
import { Result, Button, Space, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { HomeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';

const { Text } = Typography;

const NotFound: React.FC = () => {
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
          status="404"
          title="404 - 页面未找到"
          subTitle="抱歉，您访问的页面不存在。页面可能已被删除、移动或您输入的网址有误。"
          extra={
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Space wrap size="middle" style={{ justifyContent: 'center' }}>
                <Button type="primary" icon={<HomeOutlined />}>
                  <Link to="/">返回首页</Link>
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleGoBack}>
                  返回上页
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleReload}>
                  刷新页面
                </Button>
                <Button icon={<SearchOutlined />}>
                  <Link to="/">搜索内容</Link>
                </Button>
              </Space>
              <Text style={{ fontSize: 14, color: '#666', display: 'block', textAlign: 'center' }}>
                请检查网址是否正确，或从首页重新开始浏览
              </Text>
            </Space>
          }
        />
      </div>
    </div>
  );
};

export default NotFound;
