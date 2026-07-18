export const appName = '薪酬管理后台';

export const appTheme = {
  tokens: {
    colorPrimary: '#2868b2',
    colorInfo: '#2868b2',
    colorSuccess: '#2f9e72',
    colorWarning: '#d48806',
    colorError: '#d64545',
    colorBgLayout: '#f4f6f8',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorText: '#172b4d',
    colorTextSecondary: '#6b7785',
    colorBorder: '#dfe6ee',
    colorBorderSecondary: '#e9eef4',
    borderRadius: 8,
    borderRadiusLG: 8,
    controlHeight: 36,
    controlHeightSM: 30,
    fontFamily:
      'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  },
  components: {
    Button: {
      borderRadius: 7,
      controlHeight: 36,
      controlHeightSM: 30,
    },
    Card: {
      borderRadiusLG: 8,
    },
    Input: {
      activeBorderColor: '#2868b2',
      hoverBorderColor: '#7ca8d4',
    },
    Modal: {
      borderRadiusLG: 10,
    },
    Table: {
      cellPaddingBlock: 13,
      cellPaddingInline: 16,
      headerBg: '#f8fafc',
    },
  },
};

export const getAppTheme = (mode: 'light' | 'dark') => {
  if (mode === 'light') return appTheme;

  return {
    tokens: {
      ...appTheme.tokens,
      colorPrimary: '#6da8e8',
      colorInfo: '#6da8e8',
      colorSuccess: '#5fc694',
      colorWarning: '#e3a63a',
      colorError: '#f08484',
      colorBgLayout: '#111923',
      colorBgContainer: '#182331',
      colorBgElevated: '#1d2a3a',
      colorText: '#edf3f8',
      colorTextSecondary: '#a7b4c2',
      colorBorder: '#304154',
      colorBorderSecondary: '#273748',
    },
    components: {
      ...appTheme.components,
      Table: {
        ...appTheme.components.Table,
        headerBg: '#1d2a3a',
        rowHoverBg: '#243447',
      },
    },
  };
};
