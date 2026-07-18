package com.yiyundao.compensation.modules.approval.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.security.SecurityConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 审批流程配置管理
 * <p>
 * 集中管理所有审批流程的默认配置，支持运行时修改
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Component
public class ApprovalFlowConfigManager {

    /**
     * 默认审批步骤配置
     */
    private final Map<WorkflowType, List<ApprovalStepConfig>> defaultFlowConfigs = new EnumMap<>(WorkflowType.class);

    @PostConstruct
    public void init() {
        initializeDefaultFlows();
        log.info("审批流程配置管理器初始化完成，共加载 {} 种流程类型", defaultFlowConfigs.size());
    }

    /**
     * 初始化默认审批流程
     */
    private void initializeDefaultFlows() {
        // 批量支付审批流程 - 默认三审制
        List<ApprovalStepConfig> payrollApprovalSteps = List.of(
                ApprovalStepConfig.builder()
                        .stepNo(1)
                        .stepName("部门负责人审批")
                        .role(SecurityConstants.ROLE_MANAGER)
                        .timeoutHours(24)
                        .optional(false)
                        .build(),
                ApprovalStepConfig.builder()
                        .stepNo(2)
                        .stepName("财务负责人审批")
                        .role(SecurityConstants.ROLE_FINANCE)
                        .timeoutHours(24)
                        .optional(false)
                        .build(),
                ApprovalStepConfig.builder()
                        .stepNo(3)
                        .stepName("总监审批")
                        .role(SecurityConstants.ROLE_ADMIN)
                        .timeoutHours(48)
                        .optional(false)
                        .finalStep(true)
                        .build()
        );
        defaultFlowConfigs.put(WorkflowType.BATCH, payrollApprovalSteps);
        defaultFlowConfigs.put(WorkflowType.PAYROLL_DISTRIBUTION, payrollApprovalSteps);

        // 临时支付审批流程 - 两审制
        defaultFlowConfigs.put(WorkflowType.ADHOC, List.of(
                ApprovalStepConfig.builder()
                        .stepNo(1)
                        .stepName("直接上级审批")
                        .role(SecurityConstants.ROLE_MANAGER)
                        .timeoutHours(24)
                        .optional(false)
                        .build(),
                ApprovalStepConfig.builder()
                        .stepNo(2)
                        .stepName("财务审批")
                        .role(SecurityConstants.ROLE_FINANCE)
                        .timeoutHours(24)
                        .optional(false)
                        .finalStep(true)
                        .build()
        ));

        // 架构外员工审批流程 - 仅管理员审批
        defaultFlowConfigs.put(WorkflowType.OFFLINE, List.of(
                ApprovalStepConfig.builder()
                        .stepNo(1)
                        .stepName("管理员审批")
                        .role(SecurityConstants.ROLE_ADMIN)
                        .timeoutHours(24)
                        .optional(false)
                        .finalStep(true)
                        .build()
        ));

        defaultFlowConfigs.put(WorkflowType.EMPLOYEE_PROFILE_CHANGE, defaultFlowConfigs.get(WorkflowType.OFFLINE));
        defaultFlowConfigs.put(WorkflowType.PLATFORM_BIND, defaultFlowConfigs.get(WorkflowType.OFFLINE));

        // 权限授权审批流程 - 仅管理员审批
        defaultFlowConfigs.put(WorkflowType.PERMISSION, List.of(
                ApprovalStepConfig.builder()
                        .stepNo(1)
                        .stepName("管理员审批")
                        .role(SecurityConstants.ROLE_ADMIN)
                        .timeoutHours(24)
                        .optional(false)
                        .finalStep(true)
                        .build()
        ));

        // 薪酬异议审批流程 - 负责人 -> 财务 -> 老板（可通过配置覆盖）
        defaultFlowConfigs.put(WorkflowType.PAYROLL_DISPUTE, List.of(
                ApprovalStepConfig.builder()
                        .stepNo(1)
                        .stepName("负责人核实")
                        .role(SecurityConstants.ROLE_MANAGER)
                        .timeoutHours(24)
                        .optional(false)
                        .build(),
                ApprovalStepConfig.builder()
                        .stepNo(2)
                        .stepName("财务复核")
                        .role(SecurityConstants.ROLE_FINANCE)
                        .timeoutHours(24)
                        .optional(false)
                        .build(),
                ApprovalStepConfig.builder()
                        .stepNo(3)
                        .stepName("老板终审")
                        .role(SecurityConstants.ROLE_ADMIN)
                        .timeoutHours(48)
                        .optional(true)
                        .finalStep(true)
                        .build()
        ));
    }

    /**
     * 获取指定流程类型的默认审批步骤
     *
     * @param workflowType 流程类型
     * @return 审批步骤配置列表
     */
    public List<ApprovalStepConfig> getDefaultSteps(WorkflowType workflowType) {
        return defaultFlowConfigs.getOrDefault(workflowType, Collections.emptyList());
    }

    /**
     * 检查流程类型是否已配置
     *
     * @param workflowType 流程类型
     * @return 是否已配置
     */
    public boolean isConfigured(WorkflowType workflowType) {
        return defaultFlowConfigs.containsKey(workflowType);
    }

    /**
     * 获取所有流程类型
     *
     * @return 流程类型集合
     */
    public Set<WorkflowType> getAllWorkflowTypes() {
        return defaultFlowConfigs.keySet();
    }

    /**
     * 审批步骤配置
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApprovalStepConfig {

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
        @JsonProperty("role")
        private String role;

        /**
         * 指定审批人ID（优先于角色）
         */
        @JsonProperty("approverId")
        private Long approverId;

        /**
         * 指定审批人用户名（优先于角色）
         */
        @JsonProperty("approverUsername")
        private String approverUsername;

        /**
         * 超时时间（小时）
         */
        @JsonProperty("timeoutHours")
        private Integer timeoutHours;

        /**
         * 是否可选（未找到审批人时跳过）
         */
        private Boolean optional;

        /**
         * 是否为最终步骤
         */
        @JsonProperty("finalStep")
        private Boolean finalStep;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final ApprovalStepConfig config = new ApprovalStepConfig();

            public Builder stepNo(Integer stepNo) {
                config.stepNo = stepNo;
                return this;
            }

            public Builder stepName(String stepName) {
                config.stepName = stepName;
                return this;
            }

            public Builder role(String role) {
                config.role = role;
                return this;
            }

            public Builder approverId(Long approverId) {
                config.approverId = approverId;
                return this;
            }

            public Builder approverUsername(String approverUsername) {
                config.approverUsername = approverUsername;
                return this;
            }

            public Builder timeoutHours(Integer timeoutHours) {
                config.timeoutHours = timeoutHours;
                return this;
            }

            public Builder optional(Boolean optional) {
                config.optional = optional;
                return this;
            }

            public Builder finalStep(Boolean finalStep) {
                config.finalStep = finalStep;
                return this;
            }

            public ApprovalStepConfig build() {
                return config;
            }
        }
    }
}
