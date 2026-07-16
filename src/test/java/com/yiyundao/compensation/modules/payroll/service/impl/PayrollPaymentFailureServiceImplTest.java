package com.yiyundao.compensation.modules.payroll.service.impl;

import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollPaymentFailureServiceImplTest {

    @Test
    void retryShouldKeepFailureRetryingWhenPaymentBatchCreationSucceeds() {
        PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
        PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);
        when(payrollBatchService.getById(20L)).thenReturn(payrollBatch(null));
        when(payrollBatchService.retryCreatePaymentBatch(20L, true)).thenReturn(true);
        when(payrollBatchService.getById(20L)).thenReturn(payrollBatch(null), payrollBatch("PB-20"));

        PayrollPaymentFailureServiceImpl service = new InMemoryPayrollPaymentFailureService(payrollBatchService, payrollPaymentService);
        PayrollPaymentFailure failure = new PayrollPaymentFailure();
        failure.setId(1L);
        failure.setWorkflowId(10L);
        failure.setPayrollBatchId(20L);
        failure.setStatus(PayrollPaymentFailureServiceImpl.STATUS_UNRESOLVED);
        service.save(failure);

        PayrollPaymentFailure retried = service.retry(1L, true);

        assertThat(retried.getStatus()).isEqualTo(PayrollPaymentFailureServiceImpl.STATUS_RETRYING);
        assertThat(retried.getResolvedTime()).isNull();
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getPaymentBatchNo()).isEqualTo("PB-20");
        verify(payrollBatchService).retryCreatePaymentBatch(20L, true);
    }

    @Test
    void retryShouldUseFailedPaymentRetryWhenPayrollBatchAlreadyHasPaymentBatch() {
        PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
        PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);
        when(payrollBatchService.getById(20L)).thenReturn(payrollBatch("PB-EXISTING-20"));

        PayrollPaymentFailureServiceImpl service = new InMemoryPayrollPaymentFailureService(payrollBatchService, payrollPaymentService);
        PayrollPaymentFailure failure = new PayrollPaymentFailure();
        failure.setId(2L);
        failure.setWorkflowId(10L);
        failure.setPayrollBatchId(20L);
        failure.setStatus(PayrollPaymentFailureServiceImpl.STATUS_UNRESOLVED);
        service.save(failure);

        PayrollPaymentFailure retried = service.retry(2L, true);

        assertThat(retried.getStatus()).isEqualTo(PayrollPaymentFailureServiceImpl.STATUS_RETRYING);
        assertThat(retried.getPaymentBatchNo()).isEqualTo("PB-EXISTING-20");
        verify(payrollPaymentService).retryFailedPayment(20L, true);
        verify(payrollBatchService, never()).retryCreatePaymentBatch(20L, true);
    }

    @Test
    void retryShouldSkipWhenFailureWasAlreadyClaimed() {
        PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
        PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);
        when(payrollBatchService.getById(20L)).thenReturn(payrollBatch("PB-EXISTING-20"));

        InMemoryPayrollPaymentFailureService service =
                new InMemoryPayrollPaymentFailureService(payrollBatchService, payrollPaymentService);
        service.claimRetryResult = false;
        PayrollPaymentFailure failure = new PayrollPaymentFailure();
        failure.setId(3L);
        failure.setWorkflowId(10L);
        failure.setPayrollBatchId(20L);
        failure.setStatus(PayrollPaymentFailureServiceImpl.STATUS_UNRESOLVED);
        service.save(failure);

        PayrollPaymentFailure retried = service.retry(3L, true);

        assertThat(retried.getStatus()).isEqualTo(PayrollPaymentFailureServiceImpl.STATUS_UNRESOLVED);
        verify(payrollPaymentService, never()).retryFailedPayment(20L, true);
        verify(payrollBatchService, never()).retryCreatePaymentBatch(20L, true);
    }

    @Test
    void retryShouldReturnRetryingFailureWithoutTriggeringAgain() {
        PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
        PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);

        InMemoryPayrollPaymentFailureService service =
                new InMemoryPayrollPaymentFailureService(payrollBatchService, payrollPaymentService);
        PayrollPaymentFailure failure = new PayrollPaymentFailure();
        failure.setId(4L);
        failure.setWorkflowId(10L);
        failure.setPayrollBatchId(20L);
        failure.setStatus(PayrollPaymentFailureServiceImpl.STATUS_RETRYING);
        service.save(failure);

        PayrollPaymentFailure retried = service.retry(4L, true);

        assertThat(retried.getStatus()).isEqualTo(PayrollPaymentFailureServiceImpl.STATUS_RETRYING);
        verify(payrollPaymentService, never()).retryFailedPayment(20L, true);
        verify(payrollBatchService, never()).retryCreatePaymentBatch(20L, true);
    }


    @Test
    void recordFailureShouldUpsertByWorkflowIdAndTrimLongMessage() {
        PayrollPaymentFailureServiceImpl service = new InMemoryPayrollPaymentFailureService(
                mock(PayrollBatchService.class),
                mock(PayrollPaymentService.class));

        PayrollPaymentFailure first = service.recordFailure(10L, 20L, "payroll_batch:20", "first");
        PayrollPaymentFailure second = service.recordFailure(10L, 20L, "payroll_batch:20", "x".repeat(1200));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getErrorMessage()).hasSize(1000);
        assertThat(service.list()).hasSize(1);
    }

    private static final class InMemoryPayrollPaymentFailureService extends PayrollPaymentFailureServiceImpl {
        private final List<PayrollPaymentFailure> store = new ArrayList<>();
        private boolean claimRetryResult = true;

        InMemoryPayrollPaymentFailureService(PayrollBatchService payrollBatchService,
                                             PayrollPaymentService payrollPaymentService) {
            super(provider(payrollBatchService), provider(payrollPaymentService));
        }

        @Override
        public PayrollPaymentFailure getById(java.io.Serializable id) {
            return store.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public PayrollPaymentFailure getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollPaymentFailure> queryWrapper) {
            return store.stream().filter(item -> item.getWorkflowId().equals(10L)).findFirst().orElse(null);
        }

        @Override
        public boolean saveOrUpdate(PayrollPaymentFailure entity) {
            if (entity.getId() == null) {
                entity.setId((long) store.size() + 1);
                store.add(entity);
                return true;
            }
            updateById(entity);
            return true;
        }

        @Override
        public boolean save(PayrollPaymentFailure entity) {
            if (entity.getId() == null) {
                entity.setId((long) store.size() + 1);
            }
            store.add(entity);
            return true;
        }

        @Override
        public boolean updateById(PayrollPaymentFailure entity) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(entity.getId())) {
                    store.set(i, entity);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean update(com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollPaymentFailure> updateWrapper) {
            if (!claimRetryResult) {
                return false;
            }
            PayrollPaymentFailure failure = store.stream()
                    .filter(item -> !PayrollPaymentFailureServiceImpl.STATUS_RESOLVED.equals(item.getStatus())
                            && !PayrollPaymentFailureServiceImpl.STATUS_RETRYING.equals(item.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (failure == null) {
                return false;
            }
            failure.setStatus(PayrollPaymentFailureServiceImpl.STATUS_RETRYING);
            failure.setRetryCount((failure.getRetryCount() == null ? 0 : failure.getRetryCount()) + 1);
            failure.setLastRetryTime(java.time.LocalDateTime.now());
            return true;
        }

        @Override
        public List<PayrollPaymentFailure> list() {
            return store;
        }

        private static <T> ObjectProvider<T> provider(T bean) {
            return new ObjectProvider<>() {
                @Override
                public T getObject(Object... args) {
                    return bean;
                }

                @Override
                public T getIfAvailable() {
                    return bean;
                }

                @Override
                public T getIfUnique() {
                    return bean;
                }

                @Override
                public T getObject() {
                    return bean;
                }
            };
        }
    }

    private static com.yiyundao.compensation.modules.payroll.entity.PayrollBatch payrollBatch(String paymentBatchNo) {
        com.yiyundao.compensation.modules.payroll.entity.PayrollBatch payrollBatch =
                new com.yiyundao.compensation.modules.payroll.entity.PayrollBatch();
        payrollBatch.setId(20L);
        payrollBatch.setPaymentBatchNo(paymentBatchNo);
        return payrollBatch;
    }
}
