package com.yiyundao.compensation.dto.dashboard;

import lombok.Data;

@Data
public class SystemComponentStatusDto {
    private String name;      // 组件名称
    private Double runRate;   // 运行率（百分比）
    private String status;    // 在线/同步中/警告
}

