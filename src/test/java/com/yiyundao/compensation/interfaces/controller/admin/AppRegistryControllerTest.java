package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.app.AppRegistryResponse;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppRegistryControllerTest {

    private AppRegistryService appRegistryService;
    private AppRegistryController controller;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, AppRegistry.class.getName());
        assistant.setCurrentNamespace(AppRegistry.class.getName());
        TableInfoHelper.initTableInfo(assistant, AppRegistry.class);
    }

    @BeforeEach
    void setUp() {
        appRegistryService = mock(AppRegistryService.class);
        controller = new AppRegistryController(appRegistryService);
    }

    @Test
    void detailShouldReturnNotFoundWhenAppMissing() {
        when(appRegistryService.getById(99L)).thenReturn(null);

        ApiResponse<AppRegistryResponse> response = controller.detail(99L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("应用不存在");
    }

    @Test
    void listShouldGroupKeywordConditionBeforeStatusFilter() {
        when(appRegistryService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0));

        controller.list(1, 10, " partner ", "enabled");

        ArgumentCaptor<LambdaQueryWrapper<AppRegistry>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(appRegistryService).page(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();

        assertThat(sqlSegment)
                .contains("(app_name LIKE")
                .contains("OR client_id LIKE")
                .contains(") AND status =");
    }

    @Test
    void listShouldClampPageAndSizeBeforeQuerying() {
        when(appRegistryService.page(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 200, 0));

        controller.list(-1, 1000, null, null);

        ArgumentCaptor<Page<AppRegistry>> captor = ArgumentCaptor.forClass(Page.class);
        verify(appRegistryService).page(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
    }
}
