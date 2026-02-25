package com.yiyundao.compensation.manual;

import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.approval.service.ApprovalStepService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Manual inspection tool for approval workflow")
class ApprovalDebugTest {

    @Autowired
    private ApprovalEngine approvalEngine;
    @Autowired
    private ApprovalStepService approvalStepService;
    @Autowired
    private SysUserService sysUserService;

    @Test
    void printWorkflow2() {
        ApprovalWorkflow wf = approvalEngine.getById(2L);
        System.out.println("WF=" + wf);
        for (ApprovalStep step : approvalStepService.lambdaQuery().eq(ApprovalStep::getWorkflowId, 2L).list()) {
            System.out.println("STEP=" + step);
        }
        System.out.println("yuyu=" + sysUserService.findByUsername("yuyu"));
    }
}
