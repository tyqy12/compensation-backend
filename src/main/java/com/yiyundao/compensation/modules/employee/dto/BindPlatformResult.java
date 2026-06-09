package com.yiyundao.compensation.modules.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 平台绑定结果响应DTO
 * <p>
 * 包含完整的绑定结果信息，便于前端展示和追溯。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "平台绑定结果")
public class BindPlatformResult {

    /**
     * 绑定结果状态
     */
    @Schema(description = "绑定结果状态")
    private BindResult result;

    /**
     * 结果描述
     */
    @Schema(description = "结果描述")
    private String message;

    /**
     * 员工ID
     */
    @Schema(description = "员工ID")
    private Long employeeId;

    /**
     * 员工工号
     */
    @Schema(description = "员工工号")
    private String employeeNo;

    /**
     * 员工姓名
     */
    @Schema(description = "员工姓名")
    private String employeeName;

    /**
     * 平台提供方（wechat/dingtalk/feishu）
     */
    @Schema(description = "平台提供方")
    private String provider;

    /**
     * 平台主体ID
     */
    @Schema(description = "平台主体ID")
    private String subjectId;

    /**
     * 关联的系统用户ID（绑定成功后）
     */
    @Schema(description = "关联的系统用户ID")
    private Long userId;

    /**
     * 审批流程ID（待审批时）
     */
    @Schema(description = "审批流程ID")
    private Long workflowId;

    /**
     * 审批类型（待审批时）
     */
    @Schema(description = "审批类型")
    private String workflowType;

    /**
     * 冲突信息（冲突时）
     */
    @Schema(description = "冲突信息")
    private ConflictInfo conflictInfo;

    /**
     * 操作时间
     */
    @Schema(description = "操作时间")
    private LocalDateTime operationTime;

    /**
     * 冲突信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "冲突信息")
    public static class ConflictInfo {

        /**
         * 冲突类型：PLATFORM_OCCUPIED / EMPLOYEE_BOUND_OTHER
         */
        @Schema(description = "冲突类型")
        private String conflictType;

        /**
         * 被占用的员工ID
         */
        @Schema(description = "被占用的员工ID")
        private Long occupiedEmployeeId;

        /**
         * 被占用的员工姓名
         */
        @Schema(description = "被占用的员工姓名")
        private String occupiedEmployeeName;

        /**
         * 被占用的员工工号
         */
        @Schema(description = "被占用的员工工号")
        private String occupiedEmployeeNo;

        /**
         * 被占用的系统用户ID
         */
        @Schema(description = "被占用的系统用户ID")
        private Long occupiedUserId;

        /**
         * 被占用的平台提供方
         */
        @Schema(description = "被占用的平台提供方")
        private String occupiedProvider;

        /**
         * 被占用的平台主体ID
         */
        @Schema(description = "被占用的平台主体ID")
        private String occupiedSubjectId;

        /**
         * 冲突详情描述
         */
        @Schema(description = "冲突详情")
        private String detail;
    }

    /**
     * 创建成功结果
     */
    public static BindPlatformResult success(Long employeeId, String employeeNo, String employeeName,
                                             String provider, String subjectId, Long userId) {
        return BindPlatformResult.builder()
                .result(BindResult.SUCCESS)
                .message("绑定成功")
                .employeeId(employeeId)
                .employeeNo(employeeNo)
                .employeeName(employeeName)
                .provider(provider)
                .subjectId(subjectId)
                .userId(userId)
                .operationTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建已绑定结果（无需重复绑定）
     */
    public static BindPlatformResult alreadyBound(Long employeeId, String employeeNo, String employeeName,
                                                  String provider, String subjectId) {
        return BindPlatformResult.builder()
                .result(BindResult.ALREADY_BOUND)
                .message("已是同一平台账号，无需重复绑定")
                .employeeId(employeeId)
                .employeeNo(employeeNo)
                .employeeName(employeeName)
                .provider(provider)
                .subjectId(subjectId)
                .operationTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建待审批结果
     */
    public static BindPlatformResult pendingApproval(Long employeeId, String employeeNo, String employeeName,
                                                     String provider, String subjectId,
                                                     Long workflowId, String workflowType,
                                                     ConflictInfo conflictInfo) {
        return BindPlatformResult.builder()
                .result(BindResult.PENDING_APPROVAL)
                .message("平台账号冲突，已发起审批流程")
                .employeeId(employeeId)
                .employeeNo(employeeNo)
                .employeeName(employeeName)
                .provider(provider)
                .subjectId(subjectId)
                .workflowId(workflowId)
                .workflowType(workflowType)
                .conflictInfo(conflictInfo)
                .operationTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     */
    public static BindPlatformResult failed(BindResult result, String message) {
        return BindPlatformResult.builder()
                .result(result)
                .message(message)
                .operationTime(LocalDateTime.now())
                .build();
    }
}
