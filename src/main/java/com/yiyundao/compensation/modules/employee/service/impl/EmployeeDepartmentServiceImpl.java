package com.yiyundao.compensation.modules.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.EmployeeDepartmentMapper;
import com.yiyundao.compensation.modules.employee.entity.EmployeeDepartment;
import com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDepartmentServiceImpl extends ServiceImpl<EmployeeDepartmentMapper, EmployeeDepartment> implements EmployeeDepartmentService {

    @Override
    public void replaceDepartments(Long employeeId, String platformType, List<String> deptNames) {
        // 删除旧的关联
        remove(new LambdaQueryWrapper<EmployeeDepartment>().eq(EmployeeDepartment::getEmployeeId, employeeId));
        if (deptNames == null || deptNames.isEmpty()) return;
        int i = 0;
        for (String name : deptNames) {
            EmployeeDepartment ed = new EmployeeDepartment();
            ed.setEmployeeId(employeeId);
            ed.setPlatformType(platformType);
            ed.setDeptName(name);
            ed.setPrimary(i == 0);
            ed.setOrderNum(i + 1);
            save(ed);
            i++;
        }
        log.info("更新员工部门关联: employeeId={}, count={}", employeeId, deptNames.size());
    }
}

