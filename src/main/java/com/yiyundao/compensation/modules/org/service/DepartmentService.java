package com.yiyundao.compensation.modules.org.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.org.entity.Department;

public interface DepartmentService extends IService<Department> {
    void upsert(String platformType, String platformDeptId, String name, String parentPlatformDeptId, Integer orderNum);

    java.util.List<com.yiyundao.compensation.dto.org.DepartmentNodeDto> getTree(String platformType);
}
