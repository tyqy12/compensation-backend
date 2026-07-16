package com.yiyundao.compensation.infrastructure.dao;

import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApprovalWorkflowMapperTest {

    private static final long PENDING_WORKFLOW_ID = 91001L;
    private static final long FRESH_WORKFLOW_ID = 91002L;
    private static final long PROCESSED_WORKFLOW_ID = 91003L;

    @Autowired
    private ApprovalWorkflowMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM payroll_line WHERE id IN (?, ?, ?)",
                PENDING_WORKFLOW_ID, FRESH_WORKFLOW_ID, PROCESSED_WORKFLOW_ID);
        jdbcTemplate.update("DELETE FROM approval_workflow WHERE id IN (?, ?, ?)",
                PENDING_WORKFLOW_ID, FRESH_WORKFLOW_ID, PROCESSED_WORKFLOW_ID);

        insertWorkflow(PENDING_WORKFLOW_ID, "2020-01-01 00:00:00");
        insertWorkflow(FRESH_WORKFLOW_ID, "2999-01-01 00:00:00");
        insertWorkflow(PROCESSED_WORKFLOW_ID, "2020-01-01 00:00:00");
        insertLine(PENDING_WORKFLOW_ID, "objected", PENDING_WORKFLOW_ID);
        insertLine(FRESH_WORKFLOW_ID, "objected", FRESH_WORKFLOW_ID);
        insertLine(PROCESSED_WORKFLOW_ID, "objected_approved", PROCESSED_WORKFLOW_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM payroll_line WHERE id IN (?, ?, ?)",
                PENDING_WORKFLOW_ID, FRESH_WORKFLOW_ID, PROCESSED_WORKFLOW_ID);
        jdbcTemplate.update("DELETE FROM approval_workflow WHERE id IN (?, ?, ?)",
                PENDING_WORKFLOW_ID, FRESH_WORKFLOW_ID, PROCESSED_WORKFLOW_ID);
    }

    @Test
    void selectPendingPayrollDisputeWorkflowsShouldOnlyReturnStaleObjectedLines() {
        List<ApprovalWorkflow> workflows = mapper.selectPendingPayrollDisputeWorkflows(
                LocalDateTime.now().minusMinutes(10), 20);

        assertThat(workflows).extracting(ApprovalWorkflow::getId)
                .containsExactly(PENDING_WORKFLOW_ID);
    }

    private void insertWorkflow(long id, String completeTime) {
        jdbcTemplate.update("""
                INSERT INTO approval_workflow (
                    id, workflow_name, workflow_type, business_key, business_type,
                    total_steps, status, initiator_id, complete_time, deleted, version
                ) VALUES (?, 'payroll dispute', 'PAYROLL_DISPUTE', ?, 'payroll_dispute', 1, 'approved', 1, ?, 0, 0)
                """, id, "payroll_dispute:line:" + id, completeTime);
    }

    private void insertLine(long id, String status, long workflowId) {
        jdbcTemplate.update("""
                INSERT INTO payroll_line (
                    id, batch_id, employee_id, employment_type, confirmation_status,
                    dispute_workflow_id, deleted, version
                ) VALUES (?, ?, ?, 'full_time', ?, ?, 0, 0)
                """, id, id, id, status, workflowId);
    }
}
