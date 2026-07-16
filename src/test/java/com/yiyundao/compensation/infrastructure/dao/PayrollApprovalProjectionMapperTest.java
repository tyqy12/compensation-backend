package com.yiyundao.compensation.infrastructure.dao;

import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PayrollApprovalProjectionMapperTest {

    private static final long REAL_PENDING_ID = 92001L;
    private static final long REAL_APPROVED_ID = 92002L;
    private static final long ADMIN_BYPASS_ID = 92003L;
    private static final long FUTURE_ID = 92004L;
    private static final long PROCESSING_ID = 92005L;

    @Autowired
    private PayrollApprovalProjectionMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        deleteFixtures();
        insertDistribution(REAL_PENDING_ID, "2020-01-01");
        insertDistribution(REAL_APPROVED_ID, "2020-01-01");
        insertDistribution(ADMIN_BYPASS_ID, "2020-01-01");
        insertDistribution(FUTURE_ID, "2999-01-01");
        insertDistribution(PROCESSING_ID, "2020-01-01");

        insertWorkflow(REAL_PENDING_ID, "2020-01-01 00:00:00");
        insertWorkflow(REAL_APPROVED_ID, "2020-01-01 00:00:00");
        insertWorkflow(FUTURE_ID, "2020-01-01 00:00:00");
        insertWorkflow(PROCESSING_ID, "2020-01-01 00:00:00");

        insertProjection(REAL_PENDING_ID, REAL_PENDING_ID, "IN_PROGRESS");
        insertProjection(REAL_APPROVED_ID, REAL_APPROVED_ID, "APPROVED");
        insertProjection(ADMIN_BYPASS_ID, -ADMIN_BYPASS_ID, "APPROVED");
        insertProjection(FUTURE_ID, FUTURE_ID, "APPROVED");
        insertProjection(PROCESSING_ID, PROCESSING_ID, "IN_PROGRESS");
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    @Test
    void selectStalePendingDistributionApprovalsShouldCoverRealAndAdminApprovals() {
        List<PayrollApprovalProjection> projections = mapper.selectStalePendingDistributionApprovals(
                java.time.LocalDateTime.now().minusMinutes(10), 20);

        assertThat(projections).extracting(PayrollApprovalProjection::getId)
                .containsExactlyInAnyOrder(REAL_PENDING_ID, REAL_APPROVED_ID, ADMIN_BYPASS_ID);
    }

    private void insertDistribution(long id, String scheduledDate) {
        String status = id == PROCESSING_ID ? "processing" : "planned";
        jdbcTemplate.update("""
                INSERT INTO payroll_distribution (
                    id, distribution_no, batch_id, batch_revision, scheduled_date,
                    distribution_status, deleted, version
                ) VALUES (?, ?, ?, 1, ?, ?, 0, 0)
                """, id, "TEST-DIST-" + id, id, scheduledDate, status);
    }

    private void insertWorkflow(long id, String completeTime) {
        jdbcTemplate.update("""
                INSERT INTO approval_workflow (
                    id, workflow_name, workflow_type, business_key, business_type,
                    total_steps, status, initiator_id, complete_time, deleted, version
                ) VALUES (?, 'payroll distribution', 'PAYROLL_DISTRIBUTION', ?,
                    'payroll_distribution', 1, 'approved', 1, ?, 0, 0)
                """, id, "payroll_distribution:" + id, completeTime);
    }

    private void insertProjection(long id, long workflowId, String businessStatus) {
        jdbcTemplate.update("""
                INSERT INTO payroll_approval_projection (
                    id, batch_id, batch_revision, distribution_id, workflow_id,
                    business_status, deleted, version
                ) VALUES (?, ?, 1, ?, ?, ?, 0, 0)
                """, id, id, id, workflowId, businessStatus);
    }

    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM payroll_approval_projection WHERE id BETWEEN ? AND ?",
                REAL_PENDING_ID, PROCESSING_ID);
        jdbcTemplate.update("DELETE FROM payroll_distribution WHERE id BETWEEN ? AND ?",
                REAL_PENDING_ID, PROCESSING_ID);
        jdbcTemplate.update("DELETE FROM approval_workflow WHERE id BETWEEN ? AND ?",
                REAL_PENDING_ID, PROCESSING_ID);
    }
}
