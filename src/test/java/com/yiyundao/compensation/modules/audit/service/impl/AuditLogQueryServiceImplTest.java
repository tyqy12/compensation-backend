package com.yiyundao.compensation.modules.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.modules.audit.dto.AuditLogQueryRequest;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AuditLogQueryServiceImplTest {

    @Test
    void queryByPageShouldClampPageAndSizeBeforeQuerying() {
        AuditLogQueryServiceImpl service = spy(new AuditLogQueryServiceImpl());
        Page<AuditLog> page = new Page<>(1, 200, 0);
        page.setRecords(List.of());
        doReturn(page).when(service).page(any(Page.class), any(Wrapper.class));
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setPage(-1);
        request.setSize(1000);

        var result = service.queryByPage(request);

        ArgumentCaptor<Page<AuditLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(service).page(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
        assertThat(result).containsEntry("page", 1).containsEntry("size", 200);
    }
}
