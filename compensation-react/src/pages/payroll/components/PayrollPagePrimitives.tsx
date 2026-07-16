import React from 'react';
import { Statistic, Typography } from 'antd';

const { Text, Title } = Typography;

export type PayrollMetric = {
  key: string;
  title: string;
  value: string | number;
  prefix?: React.ReactNode;
  valueStyle?: React.CSSProperties;
  description?: React.ReactNode;
};

type PayrollSectionProps = {
  title: string;
  description?: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
};

export const PayrollSection: React.FC<PayrollSectionProps> = ({
  title,
  description,
  extra,
  children,
  className,
}) => (
  <section className={`payroll-section ${className || ''}`}>
    <div className="payroll-section-heading">
      <div>
        <Title level={4} className="payroll-section-title">
          {title}
        </Title>
        {description && <Text type="secondary">{description}</Text>}
      </div>
      {extra && <div className="payroll-section-extra">{extra}</div>}
    </div>
    {children}
  </section>
);

export const PayrollMetricGrid: React.FC<{ items: PayrollMetric[]; className?: string }> = ({
  items,
  className,
}) => (
  <div className={`payroll-metric-grid ${className || ''}`}>
    {items.map((item) => (
      <div className="payroll-metric-tile" key={item.key}>
        <Statistic
          title={item.title}
          value={item.value}
          prefix={item.prefix}
          styles={{ content: item.valueStyle }}
        />
        {item.description && <Text type="secondary">{item.description}</Text>}
      </div>
    ))}
  </div>
);
