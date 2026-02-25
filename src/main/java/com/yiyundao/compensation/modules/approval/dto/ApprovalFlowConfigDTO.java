package com.yiyundao.compensation.modules.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 审批流程配置DTO
 * <p>
 * 用于数据库存储和 JSON 序列化
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalFlowConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程类型编码
     */
    private String workflowType;

    /**
     * 流程名称
     */
    private String flowName;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 审批步骤列表
     */
    private List<ApprovalStepDTO> steps;

    /**
     * 审批步骤配置
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApprovalStepDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 步骤序号
         */
        private Integer stepNo;

        /**
         * 步骤名称
         */
        private String stepName;

        /**
         * 审批角色
         */
        private String role;

        /**
         * 指定审批人ID（优先级高于角色）
         */
        @JsonProperty("approverId")
        private Long approverId;

        /**
         * 指定审批人用户名（优先级高于角色）
         */
        @JsonProperty("approverUsername")
        private String approverUsername;

        /**
         * 超时时间（小时）
         */
        @JsonProperty("timeoutHours")
        private Integer timeoutHours;

        /**
         * 是否可选（未找到审批人时跳过该步骤）
         */
        private Boolean optional;

        /**
         * 是否为最终步骤
         */
        @JsonProperty("finalStep")
        private Boolean finalStep;

        /**
         * 条件表达式（可选，用于条件审批）
         */
        private String condition;
    }
}
