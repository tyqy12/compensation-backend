import React, { useState } from 'react';
import { Modal, Steps, Button, Space, Typography, Card, Tag } from 'antd';
import {
  UserOutlined,
  SettingOutlined,
  TeamOutlined,
  WalletOutlined,
  CheckCircleOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

interface NewUserGuideProps {
  visible: boolean;
  onClose: () => void;
}

const NewUserGuide: React.FC<NewUserGuideProps> = ({ visible, onClose }) => {
  const [currentStep, setCurrentStep] = useState(0);

  const steps = [
    {
      title: '欢迎使用',
      icon: <UserOutlined />,
      content: (
        <div>
          <Title level={4}>欢迎来到薪酬管理系统！</Title>
          <Paragraph>
            这个系统帮助您高效管理员工薪酬支付、用户绑定和组织同步等工作。
            让我们快速了解一下主要功能。
          </Paragraph>
          <Card size="small" style={{ backgroundColor: '#f6ffed', border: '1px solid #b7eb8f' }}>
            <Paragraph style={{ margin: 0 }}>
              💡 <Text strong>提示：</Text>您可以随时点击右上角的帮助按钮重新查看引导。
            </Paragraph>
          </Card>
        </div>
      ),
    },
    {
      title: '系统配置',
      icon: <SettingOutlined />,
      content: (
        <div>
          <Title level={4}>首先进行系统配置</Title>
          <Paragraph>在开始使用之前，建议您先完成以下配置：</Paragraph>
          <div className="guide-checklist">
            {[
              { title: '集成配置', desc: '配置微信等第三方平台接入', urgent: true },
              { title: '组织同步', desc: '同步公司组织架构和部门信息', urgent: true },
              { title: '用户绑定', desc: '管理员工与平台账号的绑定关系', urgent: false },
            ].map((item) => (
              <div className="guide-checklist-item" key={item.title}>
                <div>
                  <Text>{item.title}</Text>
                  {item.urgent && <Tag color="orange">建议优先</Tag>}
                </div>
                <Text type="secondary">{item.desc}</Text>
              </div>
            ))}
          </div>
        </div>
      ),
    },
    {
      title: '员工管理',
      icon: <TeamOutlined />,
      content: (
        <div>
          <Title level={4}>管理员工信息</Title>
          <Paragraph>员工管理是系统的核心功能之一：</Paragraph>
          <Card size="small" style={{ marginBottom: '16px' }}>
            <Space size="large" style={{ width: '100%' }}>
              <Text strong>主要功能：</Text>
              <Text>• 查看员工列表和详细信息</Text>
              <Text>• 管理员工的平台绑定状态</Text>
              <Text>• 导入和更新员工数据</Text>
              <Text>• 查看员工的支付历史</Text>
            </Space>
          </Card>
          <Card size="small" style={{ backgroundColor: '#fff7e6', border: '1px solid #ffd591' }}>
            <Text>
              💡 <Text strong>建议：</Text>定期检查员工绑定状态，确保支付能够正常进行。
            </Text>
          </Card>
        </div>
      ),
    },
    {
      title: '支付管理',
      icon: <WalletOutlined />,
      content: (
        <div>
          <Title level={4}>薪酬支付流程</Title>
          <Paragraph>系统支持批量薪酬支付，流程简单高效：</Paragraph>
          <Steps
            orientation="vertical"
            size="small"
            current={-1}
            items={[
              { title: '创建支付批次', description: '上传员工薪酬数据，创建支付批次' },
              { title: '审核批次信息', description: '检查支付金额和员工信息是否正确' },
              { title: '执行批量支付', description: '系统自动向各平台发起支付请求' },
              { title: '查看支付结果', description: '跟踪支付状态，处理失败的支付' },
            ]}
          />
        </div>
      ),
    },
    {
      title: '开始使用',
      icon: <CheckCircleOutlined />,
      content: (
        <div>
          <Title level={4}>准备就绪！</Title>
          <Paragraph>现在您已经了解了系统的主要功能。以下是建议的第一步操作：</Paragraph>
          <Card>
            <div className="guide-action-list">
              {[
                { title: '1. 配置系统集成', action: '前往系统配置', priority: 'high' },
                { title: '2. 同步组织架构', action: '开始同步', priority: 'high' },
                { title: '3. 查看员工列表', action: '检查员工信息', priority: 'medium' },
                { title: '4. 创建第一个支付批次', action: '尝试支付功能', priority: 'low' },
              ].map((item) => (
                <div className="guide-action-item" key={item.title}>
                  <div>
                    <Text>{item.title}</Text>
                    <Tag
                      color={
                        item.priority === 'high'
                          ? 'red'
                          : item.priority === 'medium'
                            ? 'orange'
                            : 'green'
                      }
                    >
                      {item.priority === 'high'
                        ? '优先'
                        : item.priority === 'medium'
                          ? '建议'
                          : '可选'}
                    </Tag>
                  </div>
                  <Button type="link" size="small">
                    {item.action}
                  </Button>
                </div>
              ))}
            </div>
          </Card>
        </div>
      ),
    },
  ];

  const handleNext = () => {
    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1);
    }
  };

  const handlePrev = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const handleFinish = () => {
    onClose();
    // 可以在这里设置用户已完成引导的标记
    localStorage.setItem('user_guide_completed', 'true');
  };

  return (
    <Modal
      title="新手引导"
      open={visible}
      onCancel={onClose}
      width={600}
      footer={
        <Space>
          <Button onClick={onClose}>跳过引导</Button>
          {currentStep > 0 && <Button onClick={handlePrev}>上一步</Button>}
          {currentStep < steps.length - 1 ? (
            <Button type="primary" onClick={handleNext}>
              下一步
            </Button>
          ) : (
            <Button type="primary" onClick={handleFinish} icon={<PlayCircleOutlined />}>
              开始使用
            </Button>
          )}
        </Space>
      }
    >
      <div style={{ padding: '20px 0' }}>
        <Steps
          current={currentStep}
          size="small"
          style={{ marginBottom: '24px' }}
          items={steps.map((step) => ({
            title: step.title,
            icon: step.icon,
          }))}
        />

        <div style={{ minHeight: '300px' }}>{steps[currentStep].content}</div>
      </div>
    </Modal>
  );
};

export default NewUserGuide;
