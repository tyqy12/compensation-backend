import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Result, Button, Space, Typography } from 'antd';
import { HomeOutlined, ReloadOutlined, BugOutlined } from '@ant-design/icons';

const { Paragraph, Text } = Typography;

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: ErrorInfo;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    this.setState({ error, errorInfo });

    // 这里可以集成错误上报服务
    // reportErrorToService(error, errorInfo);
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.href = '/';
  };

  private handleReportBug = () => {
    const { error, errorInfo } = this.state;
    const errorReport = {
      error: error?.toString(),
      stack: error?.stack,
      componentStack: errorInfo?.componentStack,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
    };

    // 这里可以实现错误报告功能
    console.log('Error Report:', errorReport);
    // 或者打开邮件客户端
    // const subject = '应用程序错误报告';
    // const body = `错误详情：\n${JSON.stringify(errorReport, null, 2)}`;
    // window.open(`mailto:support@example.com?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`);
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Result
          status="error"
          title="应用程序出错了"
          subTitle="抱歉，应用程序遇到了一个意外错误。这通常是由于程序bug或浏览器兼容性问题导致的。"
          extra={
            <Space size="middle" style={{ display: 'flex' }}>
              <Space wrap>
                <Button type="primary" icon={<ReloadOutlined />} onClick={this.handleReload}>
                  重新加载
                </Button>
                <Button icon={<HomeOutlined />} onClick={this.handleGoHome}>
                  返回首页
                </Button>
                <Button icon={<BugOutlined />} onClick={this.handleReportBug}>
                  报告问题
                </Button>
              </Space>

              {process.env.NODE_ENV === 'development' && this.state.error && (
                <div style={{ textAlign: 'left', maxWidth: '600px', margin: '0 auto' }}>
                  <Paragraph>
                    <Text strong>错误详情（开发模式）：</Text>
                  </Paragraph>
                  <Paragraph>
                    <Text code style={{ fontSize: '12px' }}>
                      {this.state.error.toString()}
                    </Text>
                  </Paragraph>
                  {this.state.error.stack && (
                    <Paragraph>
                      <Text code style={{ fontSize: '10px', whiteSpace: 'pre-wrap' }}>
                        {this.state.error.stack}
                      </Text>
                    </Paragraph>
                  )}
                </div>
              )}

              <div style={{ fontSize: '14px', color: '#666', textAlign: 'center' }}>
                如果问题持续存在，请联系技术支持团队
              </div>
            </Space>
          }
        />
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
