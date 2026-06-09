package com.yiyundao.compensation.interfaces.controller;

import com.yiyundao.compensation.controller.FileController;
import com.yiyundao.compensation.controller.TaskScheduleController;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class InfrastructureControllerMethodSecurityTest {

    @Autowired
    private FileController fileController;

    @SpyBean
    private com.yiyundao.compensation.service.FileService fileService;

    @Autowired
    private TaskScheduleController taskScheduleController;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void fileControllerShouldRejectEmployeeRole() {
        assertThatThrownBy(() -> fileController.getUrl("docs/file.txt"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "HR")
    void fileDeleteShouldRejectNonAdminRole() {
        assertThatThrownBy(() -> fileController.delete("docs/file.txt"))
                .isInstanceOf(AccessDeniedException.class);
        verify(fileService, never()).delete(Mockito.anyString());
    }

    @Test
    @WithMockUser(roles = "HR")
    void taskScheduleControllerShouldRejectNonAdminRole() {
        assertThatThrownBy(() -> taskScheduleController.create(new TaskScheduleDTO()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
