package com.yiyundao.compensation.modules.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.app.entity.AppDataGrant;

import java.util.List;

public interface AppDataGrantService extends IService<AppDataGrant> {

    String TENANT = "tenant";
    String DEPARTMENT = "department";
    String EMPLOYEE = "employee";
    String PAYROLL_BATCH = "payroll_batch";

    AppDataGrant saveValidated(AppDataGrant grant);

    List<AppDataGrant> listActiveByAppId(Long appId);

    void revoke(Long appId, Long grantId);
}
