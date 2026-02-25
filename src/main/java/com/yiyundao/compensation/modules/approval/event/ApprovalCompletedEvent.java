package com.yiyundao.compensation.modules.approval.event;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 审批完成事件
 * <p>
 * 当审批流程完成（通过/拒绝/取消）时发布此事件。
 * 各业务模块可监听此事件并执行相应的后续处理。
 * </p>
 * <p>
 * <b>事件驱动优势：</b>
 * <ul>
 *   <li>解耦：审批引擎不需要知道具体的业务处理逻辑</li>
 *   <li>扩展：新增业务类型只需添加新的监听器</li>
 *   <li>异步：可配置异步监听器提升性能</li>
 *   <li>测试：易于编写单元测试和集成测试</li>
 * </ul>
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-02-01
 */
@Getter
public class ApprovalCompletedEvent extends ApplicationEvent {

    /**
     * 审批流程实例
     */
    private final ApprovalWorkflow workflow;

    /**
     * 最终审批状态
     */
    private final ApprovalStatus finalStatus;

    /**
     * 最终审批人ID
     * <p>
     * 保存审批完成时的审批人ID，用于审计追溯。
     * 因为 workflow.currentApproverId 在事件发布前会被清空，
     * 所以需要单独保存此字段。
     * </p>
     */
    private final Long finalApproverId;

    /**
     * 构造审批完成事件
     *
     * @param source          事件源（通常是 ApprovalEngine）
     * @param workflow        审批流程实例
     * @param finalStatus     最终审批状态
     * @param finalApproverId 最终审批人ID
     */
    public ApprovalCompletedEvent(Object source, ApprovalWorkflow workflow, ApprovalStatus finalStatus, Long finalApproverId) {
        super(source);
        this.workflow = workflow;
        this.finalStatus = finalStatus;
        this.finalApproverId = finalApproverId;
    }

    /**
     * 获取业务类型
     */
    public String getBusinessType() {
        return workflow != null ? workflow.getBusinessType() : null;
    }

    /**
     * 获取业务键
     */
    public String getBusinessKey() {
        return workflow != null ? workflow.getBusinessKey() : null;
    }

    /**
     * 获取工作流ID
     */
    public Long getWorkflowId() {
        return workflow != null ? workflow.getId() : null;
    }

    /**
     * 判断是否审批通过
     */
    public boolean isApproved() {
        return finalStatus == ApprovalStatus.APPROVED;
    }

    /**
     * 判断是否审批拒绝
     */
    public boolean isRejected() {
        return finalStatus == ApprovalStatus.REJECTED;
    }

    /**
     * 判断是否审批取消
     */
    public boolean isCancelled() {
        return finalStatus == ApprovalStatus.CANCELLED;
    }

    @Override
    public String toString() {
        return "ApprovalCompletedEvent{" +
                "workflowId=" + getWorkflowId() +
                ", businessType='" + getBusinessType() + '\'' +
                ", businessKey='" + getBusinessKey() + '\'' +
                ", finalStatus=" + finalStatus +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}
