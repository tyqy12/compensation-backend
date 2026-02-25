package com.yiyundao.compensation.dto.system;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrgSyncHistoryItemDto {
    private Long id;
    private String operation;     // ORG_SYNC / ORG_SYNC_ALL / ORG_SYNC_ASYNC / ORG_CHECK
    private String platform;      // wechat/dingtalk/feishu/all
    private String result;        // OK/FAILED/任务ID等
    private String username;      // 发起人
    private String requestUrl;    // 请求路径
    private String requestIp;     // IP
    private Long executionTime;   // 执行耗时ms
    private LocalDateTime createTime; // 时间
}

