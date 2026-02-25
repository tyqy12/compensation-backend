package com.yiyundao.compensation.interfaces.vo.approval;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 审批流程详情视图对象
 *
 * @author 芙宁娜
 * @since 2026-01-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApprovalWorkflowDetailVO extends ApprovalWorkflowVO {

    /**
     * 审批步骤列表
     */
    private List<ApprovalStepVO> steps;

    /**
     * 关联的业务信息
     */
    private Map<String, Object> businessInfo;

    /**
     * 发起人信息
     */
    private String initiatorName;

    /**
     * 当前审批人信息
     */
    private String currentApproverName;
}
