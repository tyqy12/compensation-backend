package com.yiyundao.compensation.modules.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.EmployeeDepartmentMapper;
import com.yiyundao.compensation.modules.employee.entity.EmployeeDepartment;
import com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDepartmentServiceImpl extends ServiceImpl<EmployeeDepartmentMapper, EmployeeDepartment> implements EmployeeDepartmentService {

    @Override
    @Transactional
    public void replaceDepartments(Long employeeId, String platformType, List<String> deptNames) {
        if (employeeId == null) {
            return;
        }
        String source = normalizePlatform(platformType);
        // 部门关系按平台隔离，不能因为一次平台同步清除其他平台的归属。
        LambdaQueryWrapper<EmployeeDepartment> existingQuery = new LambdaQueryWrapper<EmployeeDepartment>()
                .eq(EmployeeDepartment::getEmployeeId, employeeId);
        if (MANUAL_PLATFORM.equals(source)) {
            existingQuery.and(wrapper -> wrapper
                    .eq(EmployeeDepartment::getPlatformType, source)
                    .or().isNull(EmployeeDepartment::getPlatformType)
                    .or().eq(EmployeeDepartment::getPlatformType, ""));
        } else {
            existingQuery.eq(EmployeeDepartment::getPlatformType, source);
        }
        remove(existingQuery);

        List<String> normalizedNames = normalizeDepartmentNames(deptNames);
        if (normalizedNames.isEmpty()) {
            return;
        }

        List<EmployeeDepartment> relations = new java.util.ArrayList<>();
        int i = 0;
        for (String name : normalizedNames) {
            EmployeeDepartment ed = new EmployeeDepartment();
            ed.setEmployeeId(employeeId);
            ed.setPlatformType(source);
            ed.setDeptName(name);
            ed.setPrimary(i == 0);
            ed.setOrderNum(i + 1);
            relations.add(ed);
            i++;
        }
        if (!saveBatch(relations)) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "员工部门关系保存失败");
        }
        log.info("更新员工部门关联: employeeId={}, platform={}, count={}", employeeId, source, relations.size());
    }

    @Override
    public List<String> findDepartmentNames(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        List<EmployeeDepartment> relations = list(new QueryWrapper<EmployeeDepartment>()
                .eq("employee_id", employeeId)
                .orderByDesc("is_primary")
                .orderByAsc("order_num")
                .orderByAsc("id"));
        return relations.stream()
                .map(EmployeeDepartment::getDeptName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Override
    public Map<Long, List<String>> findDepartmentNamesByEmployeeIds(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<EmployeeDepartment> relations = list(new QueryWrapper<EmployeeDepartment>()
                .in("employee_id", ids)
                .orderByDesc("is_primary")
                .orderByAsc("order_num")
                .orderByAsc("id"));
        Map<Long, LinkedHashSet<String>> namesByEmployee = new LinkedHashMap<>();
        for (EmployeeDepartment relation : relations) {
            if (relation.getEmployeeId() == null || !StringUtils.hasText(relation.getDeptName())) {
                continue;
            }
            namesByEmployee.computeIfAbsent(relation.getEmployeeId(), ignored -> new LinkedHashSet<>())
                    .add(relation.getDeptName().trim());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        namesByEmployee.forEach((id, names) -> result.put(id, List.copyOf(names)));
        return result;
    }

    @Override
    public List<Long> findEmployeeIdsByDepartmentName(String departmentName) {
        if (!StringUtils.hasText(departmentName)) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<EmployeeDepartment>()
                .select(EmployeeDepartment::getEmployeeId)
                .eq(EmployeeDepartment::getDeptName, departmentName.trim())
                .isNotNull(EmployeeDepartment::getEmployeeId))
                .stream()
                .map(EmployeeDepartment::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> findEmployeeIdsByLocalDepartmentId(Long localDepartmentId) {
        if (localDepartmentId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<EmployeeDepartment>()
                .select(EmployeeDepartment::getEmployeeId)
                .eq(EmployeeDepartment::getLocalDeptId, localDepartmentId)
                .isNotNull(EmployeeDepartment::getEmployeeId))
                .stream()
                .map(EmployeeDepartment::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizePlatform(String platformType) {
        return StringUtils.hasText(platformType) ? platformType.trim().toLowerCase() : MANUAL_PLATFORM;
    }

    private List<String> normalizeDepartmentNames(List<String> deptNames) {
        if (deptNames == null || deptNames.isEmpty()) {
            return List.of();
        }
        List<String> normalized = deptNames.stream()
                .filter(StringUtils::hasText)
                .flatMap(name -> java.util.Arrays.stream(name.split("[,，、/]")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        for (String name : normalized) {
            if (name.length() > 200) {
                throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "部门名称长度不能超过200个字符");
            }
        }
        return normalized;
    }
}
