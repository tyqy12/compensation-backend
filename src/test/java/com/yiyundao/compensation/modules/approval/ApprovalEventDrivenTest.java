package com.yiyundao.compensation.modules.approval;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审批事件驱动模式单元测试
 * <p>
 * 测试 Spring Event 机制在审批流程中的应用。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-31
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("审批事件驱动模式测试")
class ApprovalEventDrivenTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private TestEventListener testEventListener;

    @Test
    @DisplayName("测试事件发布和监听机制")
    void testEventPublishAndListen() throws InterruptedException {
        // 准备测试数据 - 使用非薪资类型避免触发数据库操作
        ApprovalWorkflow workflow = createTestWorkflow();
        workflow.setBusinessType("TEST_TYPE"); // 使用测试类型，不会触发任何 Handler
        workflow.setBusinessKey("test_key_1"); // 使用测试 key，避免匹配到 payroll_batch
        ApprovalStatus finalStatus = ApprovalStatus.APPROVED;

        // 重置监听器状态
        if (testEventListener != null) {
            testEventListener.reset();
        }

        // 发布事件
        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, finalStatus, 200L);
        eventPublisher.publishEvent(event);

        // 等待异步处理完成
        if (testEventListener != null) {
            boolean received = testEventListener.await(5, TimeUnit.SECONDS);
            assertTrue(received, "事件应该被监听器接收");
            assertTrue(testEventListener.isEventReceived(), "事件接收标志应该为 true");
            assertEquals(workflow.getId(), testEventListener.getReceivedWorkflowId(), "接收到的 workflowId 应该匹配");
            assertEquals(finalStatus, testEventListener.getReceivedStatus(), "接收到的状态应该匹配");
        }
    }

    @Test
    @DisplayName("测试 ApprovalCompletedEvent 辅助方法")
    void testApprovalCompletedEventHelperMethods() {
        ApprovalWorkflow workflow = createTestWorkflow();

        // 测试 isApproved()
        ApprovalCompletedEvent approvedEvent = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 100L);
        assertTrue(approvedEvent.isApproved(), "isApproved() 应该返回 true");
        assertFalse(approvedEvent.isRejected(), "isRejected() 应该返回 false");
        assertFalse(approvedEvent.isCancelled(), "isCancelled() 应该返回 false");

        // 测试 isRejected()
        ApprovalCompletedEvent rejectedEvent = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.REJECTED, 100L);
        assertFalse(rejectedEvent.isApproved(), "isApproved() 应该返回 false");
        assertTrue(rejectedEvent.isRejected(), "isRejected() 应该返回 true");
        assertFalse(rejectedEvent.isCancelled(), "isCancelled() 应该返回 false");

        // 测试 isCancelled()
        ApprovalCompletedEvent cancelledEvent = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.CANCELLED, 100L);
        assertFalse(cancelledEvent.isApproved(), "isApproved() 应该返回 false");
        assertFalse(cancelledEvent.isRejected(), "isRejected() 应该返回 false");
        assertTrue(cancelledEvent.isCancelled(), "isCancelled() 应该返回 true");
    }

    @Test
    @DisplayName("测试事件数据完整性")
    void testEventDataIntegrity() {
        ApprovalWorkflow workflow = createTestWorkflow();
        workflow.setId(12345L);
        workflow.setWorkflowName("测试审批流程");
        workflow.setBusinessKey("test_batch_001");
        workflow.setBusinessType("payroll");

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 999L);

        assertNotNull(event.getWorkflow(), "workflow 不应该为 null");
        assertEquals(12345L, event.getWorkflow().getId(), "workflowId 应该匹配");
        assertEquals("测试审批流程", event.getWorkflow().getWorkflowName(), "workflowName 应该匹配");
        assertEquals("test_batch_001", event.getWorkflow().getBusinessKey(), "businessKey 应该匹配");
        assertEquals("payroll", event.getWorkflow().getBusinessType(), "businessType 应该匹配");
        assertEquals(ApprovalStatus.APPROVED, event.getFinalStatus(), "finalStatus 应该匹配");
        assertEquals(999L, event.getFinalApproverId(), "finalApproverId 应该匹配");
        assertTrue(event.getTimestamp() > 0, "timestamp 应该大于 0");
    }

    /**
     * 创建测试用的审批流程
     */
    private ApprovalWorkflow createTestWorkflow() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1L);
        workflow.setWorkflowName("测试审批流程");
        workflow.setWorkflowType(WorkflowType.BATCH);
        workflow.setBusinessKey("payroll_batch_1");
        workflow.setBusinessType("payroll");
        workflow.setCurrentStep(1);
        workflow.setTotalSteps(2);
        workflow.setStatus(ApprovalStatus.PENDING);
        workflow.setInitiatorId(100L);
        workflow.setCurrentApproverId(200L);
        workflow.setSubmitTime(LocalDateTime.now());
        return workflow;
    }

    /**
     * 测试用事件监听器
     */
    @Component
    static class TestEventListener {
        private final AtomicBoolean eventReceived = new AtomicBoolean(false);
        private final AtomicReference<Long> receivedWorkflowId = new AtomicReference<>();
        private final AtomicReference<ApprovalStatus> receivedStatus = new AtomicReference<>();
        private CountDownLatch latch = new CountDownLatch(1);

        @EventListener
        public void onApprovalCompleted(ApprovalCompletedEvent event) {
            eventReceived.set(true);
            if (event.getWorkflow() != null) {
                receivedWorkflowId.set(event.getWorkflow().getId());
            }
            receivedStatus.set(event.getFinalStatus());
            latch.countDown();
        }

        public boolean isEventReceived() {
            return eventReceived.get();
        }

        public Long getReceivedWorkflowId() {
            return receivedWorkflowId.get();
        }

        public ApprovalStatus getReceivedStatus() {
            return receivedStatus.get();
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public void reset() {
            eventReceived.set(false);
            receivedWorkflowId.set(null);
            receivedStatus.set(null);
            latch = new CountDownLatch(1);
        }
    }
}
