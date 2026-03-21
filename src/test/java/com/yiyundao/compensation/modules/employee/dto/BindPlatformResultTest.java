package com.yiyundao.compensation.modules.employee.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BindPlatformResultTest {

    @Test
    void shouldPopulateProviderAndSubjectIdForSuccess() {
        BindPlatformResult result = BindPlatformResult.success(
                1L,
                "EMP001",
                "张三",
                "wechat",
                "wx_user_001",
                100L
        );

        assertEquals(BindResult.SUCCESS, result.getResult());
        assertEquals("wechat", result.getProvider());
        assertEquals("wx_user_001", result.getSubjectId());
        assertNotNull(result.getOperationTime());
    }

    @Test
    void shouldPopulateProviderAndSubjectIdForPendingApproval() {
        BindPlatformResult.ConflictInfo conflictInfo = BindPlatformResult.ConflictInfo.builder()
                .conflictType("PLATFORM_OCCUPIED")
                .occupiedEmployeeId(2L)
                .occupiedEmployeeName("李四")
                .occupiedEmployeeNo("EMP002")
                .occupiedProvider("dingtalk")
                .occupiedSubjectId("ding_user_002")
                .detail("平台账号冲突")
                .build();

        BindPlatformResult result = BindPlatformResult.pendingApproval(
                1L,
                "EMP001",
                "张三",
                "feishu",
                "fs_user_001",
                999L,
                "PLATFORM_LINK",
                conflictInfo
        );

        assertEquals(BindResult.PENDING_APPROVAL, result.getResult());
        assertEquals("feishu", result.getProvider());
        assertEquals("fs_user_001", result.getSubjectId());
        assertEquals("dingtalk", result.getConflictInfo().getOccupiedProvider());
        assertEquals("ding_user_002", result.getConflictInfo().getOccupiedSubjectId());
        assertNotNull(result.getOperationTime());
    }
}
